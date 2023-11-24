package net.corda.libs.statemanager.impl.tests

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.utils.transaction
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.convertToMetadata
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.model.v1.resultSetAsStateEntityCollection
import net.corda.libs.statemanager.impl.repository.impl.PostgresQueryProvider
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.libs.statemanager.impl.tests.MultiThreadedTestHelper.runMultiThreadedOptimisticLockingTest
import net.corda.libs.statemanager.impl.tests.MultiThreadedTestHelper.updateStateObjects
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch

// TODO-[CORE-16663]: make database provider pluggable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateManagerIntegrationTest {
    private val maxConcurrentThreadJdbcConnections = 10
    private val dataSource = DbUtils.createDataSource(maximumPoolSize = maxConcurrentThreadJdbcConnections)

    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/statemanager/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    private val objectMapper = ObjectMapper()
    private val testUniqueId = UUID.randomUUID()
    private val queryProvider = PostgresQueryProvider()

    private val stateManager: StateManager = StateManagerImpl(
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>(),
        dataSource = dataSource,
        stateRepository = StateRepositoryImpl(queryProvider)
    )

    private fun cleanStates() = dataSource.connection.transaction {
        it.createStatement().executeUpdate("DELETE FROM state s WHERE s.key LIKE '%$testUniqueId%'")
    }

    @BeforeEach
    fun setUp() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        cleanStates()
    }

    private fun buildStateKey(index: Int) = "key_$index-$testUniqueId"

    private fun persistStateEntities(
        indexRange: IntProgression,
        version: (index: Int, key: String) -> Int,
        stateContent: (index: Int, key: String) -> String,
        metadataContent: (index: Int, key: String) -> String,
    ) = indexRange.forEach { i ->
        dataSource.connection.transaction { connection ->
            val key = buildStateKey(i)
            val stateEntity =
                StateEntity(key, stateContent(i, key).toByteArray(), metadataContent(i, key), version(i, key))

            connection.prepareStatement(queryProvider.createState).use {
                it.setString(1, stateEntity.key)
                it.setBytes(2, stateEntity.value)
                it.setInt(3, stateEntity.version)
                it.setString(4, stateEntity.metadata)
                it.executeUpdate()
            }
        }
    }

    private fun softlyAssertPersistedStateEntities(
        indexRange: IntProgression,
        version: (index: Int, key: String) -> Int,
        stateContent: (index: Int, key: String) -> String,
        metadataContent: (index: Int, key: String) -> Metadata,
    ) = dataSource.connection.transaction { connection ->
        indexRange.forEach { i ->
            val key = buildStateKey(i)
            val loadedEntity = connection
                .prepareStatement(queryProvider.findStatesByKey(1))
                .use {
                    it.setString(1, key)
                    it.executeQuery().resultSetAsStateEntityCollection()
                }.elementAt(0)

            assertSoftly {
                it.assertThat(loadedEntity.key).isEqualTo(key)
                it.assertThat(loadedEntity.modifiedTime).isNotNull
                it.assertThat(loadedEntity.version).isEqualTo(version(i, key))
                it.assertThat(loadedEntity.value).isEqualTo((stateContent(i, key).toByteArray()))
                it.assertThat(objectMapper.convertToMetadata(loadedEntity.metadata))
                    .containsExactlyInAnyOrderEntriesOf(metadataContent(i, key))
            }
        }
    }

    private fun getIntervalBetweenEntities(startEntityKey: String, finishEntityKey: String): Pair<Instant, Instant> {
        return dataSource.connection.transaction { connection ->
            val loadedEntities = connection.prepareStatement(queryProvider.findStatesByKey(2))
                .use {
                    it.setString(1, startEntityKey)
                    it.setString(2, finishEntityKey)
                    it.executeQuery().resultSetAsStateEntityCollection()
                }.sortedBy {
                    it.modifiedTime
                }

            Pair(
                loadedEntities.elementAt(0).modifiedTime,
                loadedEntities.elementAt(1).modifiedTime
            )
        }
    }

    @ValueSource(ints = [1, 10])
    @ParameterizedTest(name = "can create basic states (batch size: {0})")
    fun canCreateBasicStates(stateCount: Int) {
        val states = mutableSetOf<State>()
        for (i in 1..stateCount) {
            states.add(State(buildStateKey(i), "simpleState_$i".toByteArray()))
        }

        assertThat(stateManager.create(states)).isEmpty()
        softlyAssertPersistedStateEntities(
            (1..stateCount),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "simpleState_$i" },
            { _, _ -> metadata() }
        )
    }

    @ValueSource(ints = [1, 10])
    @ParameterizedTest(name = "can create states with custom metadata (batch size: {0})")
    fun canCreateStatesWithCustomMetadata(stateCount: Int) {
        val states = mutableSetOf<State>()
        for (i in 1..stateCount) {
            states.add(
                State(
                    buildStateKey(i),
                    "customState_$i".toByteArray(),
                    metadata = metadata("key1" to "value$i", "key2" to i)
                )
            )
        }

        assertThat(stateManager.create(states)).isEmpty()
        softlyAssertPersistedStateEntities(
            (1..stateCount),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "customState_$i" },
            { i, _ -> metadata("key1" to "value$i", "key2" to i) }
        )
    }

    @Test
    @DisplayName(value = "failures when persisting some states do not halt the entire batch")
    fun failuresWhenPersistingSomeStatesDoesNotHaltTheEntireBatch() {
        val failedSates = 5
        val totalStates = 15
        persistStateEntities(
            (1..failedSates),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )
        val states = mutableSetOf<State>()
        for (i in 1..totalStates) {
            states.add(State(buildStateKey(i), "newState_$i".toByteArray()))
        }

        val failures = stateManager.create(states)
        assertThat(failures).hasSize(failedSates)
        for (i in 1..failedSates) {
            assertThat(failures[buildStateKey(i)]).isInstanceOf(SQLException::class.java)
        }
        softlyAssertPersistedStateEntities(
            (failedSates + 1..totalStates),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "newState_$i" },
            { _, _ -> metadata() }
        )
    }

    @ValueSource(ints = [1, 10])
    @ParameterizedTest(name = "can retrieve states by key (batch size: {0})")
    fun canRetrieveStatesByKey(stateCount: Int) {
        persistStateEntities(
            (1..stateCount),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val states = stateManager.get((1..stateCount).map { buildStateKey(it) }.toSet())
        assertThat(states.size).isEqualTo(stateCount)
        for (i in 1..stateCount) {
            val key = buildStateKey(i)
            val loadedState = states[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.value).isEqualTo("existingState_$i".toByteArray())
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(State.VERSION_INITIAL_VALUE)
                it.assertThat(loadedState.metadata)
                    .containsExactlyInAnyOrderEntriesOf(mutableMapOf("k1" to "v$i", "k2" to i))
            }
        }
    }

    @ValueSource(ints = [1, 5, 10, 20, 50])
    @ParameterizedTest(name = "can update existing states (batch size: {0})")
    fun canUpdateExistingStates(stateCount: Int) {
        persistStateEntities(
            (1..stateCount),
            { i, _ -> i },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"originalK1": "v$i", "originalK2": $i}""" }
        )
        val statesToUpdate = mutableSetOf<State>()
        for (i in 1..stateCount) {
            statesToUpdate.add(
                State(buildStateKey(i), "state_$i$i".toByteArray(), i, metadata("updatedK2" to "updatedV2"))
            )
        }

        val failedUpdates = stateManager.update(statesToUpdate)

        assertThat(failedUpdates).isEmpty()
        softlyAssertPersistedStateEntities(
            (1..stateCount),
            { i, _ -> i + 1 },
            { i, _ -> "state_$i$i" },
            { _, _ -> metadata("updatedK2" to "updatedV2") }
        )
    }

    @Test
    fun `optimistic locking prevents sequentially updating states with mismatched versions and does not halt entire batch`() {
        val totalCount = 20
        persistStateEntities(
            (1..totalCount),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val allKeys = (1..totalCount).map { buildStateKey(it) }
        val conflictingKeys = (1..totalCount).filter { it % 2 == 0 }.map { buildStateKey(it) }
        val persistedStates = stateManager.get(allKeys)

        val statesToUpdateA = mutableListOf<State>()
        conflictingKeys.forEach { key ->
            val state = persistedStates[key]!!
            statesToUpdateA.add(
                State(state.key, "a_$key".toByteArray(), state.version, metadata("a" to key))
            )
        }

        assertThat(stateManager.update(statesToUpdateA)).isEmpty()

        val statesToUpdateB = mutableMapOf<String, State>()
        allKeys.forEach {
            val state = persistedStates[it]!!
            statesToUpdateB[state.key] = State(state.key, "b_$it".toByteArray(), state.version, metadata("b" to it))
        }

        val failedUpdates = stateManager.update(statesToUpdateB.values)
        assertThat(failedUpdates).containsOnlyKeys(conflictingKeys)
        assertSoftly {
            failedUpdates.values.map { state ->
                // update A has already bumped the version by 1, causing B's state update to fail
                it.assertThat(state.version).isEqualTo(statesToUpdateB[state.key]!!.version + 1)
                it.assertThat(state.value).isEqualTo("a_${state.key}".toByteArray())
                it.assertThat(state.metadata).isEqualTo(metadata("a" to state.key))
            }
        }

        softlyAssertPersistedStateEntities(
            (1..totalCount),
            { _, _ -> 1 },
            { _, key -> if (conflictingKeys.contains(key)) "a_$key" else "b_$key" },
            { _, key -> if (conflictingKeys.contains(key)) metadata("a" to key) else metadata("b" to key) },
        )
    }

    @Suppress("SpreadOperator")
    @Test
    fun `optimistic locking ensures no double updates across threads`() {
        val totalStates = 100
        val numThreads = maxConcurrentThreadJdbcConnections
        val sharedStatesPerThread = 5

        persistStateEntities(
            (1..totalStates),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val allKeys = (1..totalStates).map { buildStateKey(it) }
        val allStatesInTest = stateManager.get(allKeys).values.toList()

        val latch = CountDownLatch(numThreads)
        val threadResults = runMultiThreadedOptimisticLockingTest(
            allStatesInTest,
            numThreads,
            sharedStatesPerThread
        ) { threadIndex, stateGroup ->
            // try to make the call to update as contentious among threads as possible
            val updatedStates = updateStateObjects(stateGroup.getStatesForTest(), testUniqueId, threadIndex)
            latch.countDown()
            latch.await()
            MultiThreadedTestHelper.FailedKeysSummary(failedUpdates = stateManager.update(updatedStates).map { it.key })
        }

        val actualFailedKeys = threadResults.map { it.failedKeysSummary.failedUpdates }.flatten()
        val expectedFailedKeys = threadResults.map { it.assignedStateGrouping.overlappingStates }.flatten().map { it.key }
        assertThat(actualFailedKeys)
            .containsExactlyInAnyOrder(*expectedFailedKeys.toTypedArray())
            .withFailMessage("Expected one failure for every state shared between another thread")
    }

    @ValueSource(ints = [1, 5, 10, 20, 50])
    @ParameterizedTest(name = "can delete existing states (batch size: {0})")
    fun canDeleteExistingStates(stateCount: Int) {
        persistStateEntities(
            (1..stateCount),
            { i, _ -> i },
            { i, _ -> "stateToDelete_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val statesToDelete = mutableSetOf<State>()
        for (i in 1..stateCount) {
            statesToDelete.add(State(buildStateKey(i), "".toByteArray(), i))
        }

        assertThat(stateManager.get(statesToDelete.map { it.key })).hasSize(stateCount)
        stateManager.delete(statesToDelete)
        assertThat(stateManager.get(statesToDelete.map { it.key })).isEmpty()
    }

    @Test
    fun `optimistic locking prevents sequentially deleting states with mismatched versions and does not halt entire batch`() {
        val totalCount = 20
        persistStateEntities(
            (1..totalCount),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val allKeys = (1..totalCount).map { buildStateKey(it) }
        val conflictingKeys = (1..totalCount).filter { it % 2 == 0 }.map { buildStateKey(it) }
        val persistedStates = stateManager.get(allKeys)

        val statesToUpdate = mutableListOf<State>()
        conflictingKeys.forEach {
            val state = persistedStates[it]!!
            statesToUpdate.add(
                State(state.key, "u1_$it".toByteArray(), state.version, metadata("u1" to it))
            )
        }

        assertThat(stateManager.update(statesToUpdate)).isEmpty()

        val statesToDelete = mutableMapOf<String, State>()
        allKeys.forEach {
            val state = persistedStates[it]!!
            statesToDelete[state.key] = State(state.key, "delete".toByteArray(), state.version)
        }

        val failedDeletes = stateManager.delete(statesToDelete.values)
        assertThat(failedDeletes).containsOnlyKeys(conflictingKeys)
        assertSoftly {
            failedDeletes.values.map { state ->
                // assert the real version has bumped by one
                it.assertThat(state.version).isEqualTo(statesToDelete[state.key]!!.version + 1)
            }
        }

        softlyAssertPersistedStateEntities(
            (2..totalCount step 2),
            { _, _ -> 1 },
            { _, key -> "u1_$key" },
            { _, key -> metadata("u1" to key) },
        )
    }

    @Test
    @DisplayName(value = "can filter states by last update time")
    fun canFilterStatesByLastUpdatedTime() {
        val count = 10
        val keyIndexRange = 1..count
        persistStateEntities(
            keyIndexRange,
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { _, _ -> "{}" }
        )
        val (startTime, finishTime) = getIntervalBetweenEntities(
            buildStateKey(keyIndexRange.first),
            buildStateKey(keyIndexRange.last)
        )

        val filteredStates = stateManager.updatedBetween(IntervalFilter(startTime, finishTime))
        assertThat(filteredStates).hasSize(count)

        for (i in keyIndexRange) {
            val key = buildStateKey(i)
            val loadedState = filteredStates[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.value).isEqualTo("state_$i".toByteArray())
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(State.VERSION_INITIAL_VALUE)
                it.assertThat(loadedState.metadata).containsExactlyInAnyOrderEntriesOf(emptyMap())
            }
        }

        // Update half the states, filter by updated time and check results again
        val keyUpdateIndexRange = 1..count / 2
        val statesToUpdate = mutableSetOf<State>()
        for (i in keyUpdateIndexRange) {
            statesToUpdate.add(
                State(
                    buildStateKey(i),
                    "updated_state_$i".toByteArray(),
                    State.VERSION_INITIAL_VALUE,
                    metadata("k1" to "v$i")
                )
            )
        }

        assertThat(stateManager.update(statesToUpdate)).isEmpty()
        val (updateStartTime, updateFinishTime) = getIntervalBetweenEntities(
            buildStateKey(keyUpdateIndexRange.first),
            buildStateKey(keyUpdateIndexRange.last)
        )

        val filteredUpdateStates = stateManager.updatedBetween(IntervalFilter(updateStartTime, updateFinishTime))
        assertThat(filteredUpdateStates).hasSize(count / 2)

        for (i in keyUpdateIndexRange) {
            val key = buildStateKey(i)
            val loadedState = filteredUpdateStates[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.value).isEqualTo("updated_state_$i".toByteArray())
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(State.VERSION_INITIAL_VALUE + 1)
                it.assertThat(loadedState.metadata).containsExactlyInAnyOrderEntriesOf(mutableMapOf("k1" to "v$i"))
            }
        }
    }

    @Test
    @DisplayName(value = "can filter states using simple comparisons on metadata values")
    fun canFilterStatesUsingSimpleComparisonsOnMetadataValues() {
        val count = 20
        persistStateEntities(
            (1..count),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i, "boolean": ${i % 2 == 0}, "string": "random_$i" }""" }
        )

        // Numeric
        assertThat(stateManager.findByMetadata(MetadataFilter("number", Operation.Equals, count))).hasSize(1)
        assertThat(stateManager.findByMetadata(MetadataFilter("number", Operation.NotEquals, count))).hasSize(count - 1)
        assertThat(stateManager.findByMetadata(MetadataFilter("number", Operation.GreaterThan, count))).isEmpty()
        assertThat(stateManager.findByMetadata(MetadataFilter("number", Operation.LesserThan, count))).hasSize(count - 1)

        // String
        assertThat(stateManager.findByMetadata(MetadataFilter("string", Operation.Equals, "random_$count"))).hasSize(1)
        assertThat(stateManager.findByMetadata(MetadataFilter("string", Operation.NotEquals, "random"))).hasSize(count)
        assertThat(stateManager.findByMetadata(MetadataFilter("string", Operation.GreaterThan, "random_1"))).hasSize(count - 1)
        assertThat(stateManager.findByMetadata(MetadataFilter("string", Operation.LesserThan, "random_1"))).isEmpty()

        // Booleans
        assertThat(stateManager.findByMetadata(MetadataFilter("boolean", Operation.Equals, true))).hasSize(count / 2)
        assertThat(stateManager.findByMetadata(MetadataFilter("boolean", Operation.NotEquals, true))).hasSize(count / 2)
        assertThat(stateManager.findByMetadata(MetadataFilter("boolean", Operation.GreaterThan, false))).hasSize(count / 2)
        assertThat(stateManager.findByMetadata(MetadataFilter("boolean", Operation.LesserThan, false))).isEmpty()
    }

    @Test
    @DisplayName(value = "can filter states using multiple conjunctive comparisons on metadata values")
    fun canFilterStatesUsingMultipleConjunctiveComparisonsOnMetadataValues() {
        val count = 20
        persistStateEntities(
            (1..count),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i, "boolean": ${i % 2 == 0}, "string": "random_$i" }""" }
        )

        assertThat(stateManager.findByMetadataMatchingAll(listOf(
            MetadataFilter("number", Operation.GreaterThan, 5),
            MetadataFilter("number", Operation.LesserThan, 7),
            MetadataFilter("boolean", Operation.Equals, true),
            MetadataFilter("string", Operation.Equals, "random_6"),
        ))).hasSize(1)

        assertThat(stateManager.findByMetadataMatchingAll(listOf(
            MetadataFilter("number", Operation.GreaterThan, 5),
            MetadataFilter("number", Operation.LesserThan, 7),
            MetadataFilter("boolean", Operation.Equals, true),
            MetadataFilter("string", Operation.Equals, "non_existing_value"),
        ))).isEmpty()

        assertThat(stateManager.findByMetadataMatchingAll(listOf(
            MetadataFilter("number", Operation.GreaterThan, 0),
            MetadataFilter("boolean", Operation.Equals, true),
        ))).hasSize(count / 2)

        assertThat(stateManager.findByMetadataMatchingAll(listOf(
            MetadataFilter("number", Operation.NotEquals, 0),
            MetadataFilter("string", Operation.Equals, "non_existing_key"),
        ))).isEmpty()
    }

    @Test
    @DisplayName(value = "can filter states using multiple disjunctive comparisons on metadata values")
    fun canFilterStatesUsingMultipleDisjunctiveComparisonsOnMetadataValues() {
        val count = 20
        persistStateEntities(
            (1..count),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i, "boolean": ${i % 2 == 0}, "string": "random_$i" }""" }
        )

        assertThat(stateManager.findByMetadataMatchingAny(listOf(
            MetadataFilter("number", Operation.Equals, 5),
            MetadataFilter("number", Operation.Equals, 7),
            MetadataFilter("string", Operation.Equals, "random_6"),
        ))).hasSize(3)

        assertThat(stateManager.findByMetadataMatchingAny(listOf(
            MetadataFilter("number", Operation.GreaterThan, 5),
            MetadataFilter("number", Operation.LesserThan, 7),
        ))).hasSize(count)

        assertThat(stateManager.findByMetadataMatchingAny(listOf(
            MetadataFilter("boolean", Operation.Equals, false),
            MetadataFilter("boolean", Operation.Equals, true),
        ))).hasSize(count)

        assertThat(stateManager.findByMetadataMatchingAny(listOf(
            MetadataFilter("number", Operation.GreaterThan, 20),
            MetadataFilter("boolean", Operation.Equals, true),
        ))).hasSize(count / 2)

        assertThat(stateManager.findByMetadataMatchingAny(listOf(
            MetadataFilter("number", Operation.Equals, 0),
            MetadataFilter("string", Operation.Equals, "non_existing_key"),
        ))).isEmpty()
    }

    @Test
    @DisplayName(value = "can filter states using simple comparisons on metadata values and last update time")
    fun canFilterStatesUsingSimpleComparisonsOnMetadataValuesAndLastUpdatedTime() {
        val count = 20
        val half = count / 2
        val keyIndexRange = 1..count
        persistStateEntities(
            (keyIndexRange),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i }""" }
        )
        val (halfTime, finishTime) = getIntervalBetweenEntities(
            buildStateKey(keyIndexRange.elementAt(half)),
            buildStateKey(keyIndexRange.last)
        )

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataFilter(
                IntervalFilter(halfTime, finishTime),
                MetadataFilter("number", Operation.Equals, 1)
            )
        ).hasSize(0)
        assertThat(
            stateManager.findUpdatedBetweenWithMetadataFilter(
                IntervalFilter(halfTime, finishTime),
                MetadataFilter("number", Operation.NotEquals, 1)
            )
        ).hasSize(half)
        assertThat(
            stateManager.findUpdatedBetweenWithMetadataFilter(
                IntervalFilter(halfTime, finishTime),
                MetadataFilter("number", Operation.GreaterThan, half)
            )
        ).hasSize(half)
        assertThat(
            stateManager.findUpdatedBetweenWithMetadataFilter(
                IntervalFilter(halfTime, finishTime),
                MetadataFilter("number", Operation.LesserThan, count)
            )
        ).hasSize(half - 1)
        assertThat(
            stateManager.findUpdatedBetweenWithMetadataFilter(
                IntervalFilter(finishTime, finishTime.plusSeconds(30)),
                MetadataFilter("number", Operation.LesserThan, count)
            )
        ).isEmpty()
    }

    @Test
    @DisplayName(value = "can filter states using multiple conjunctive comparisons on metadata values and last updated time")
    fun canFilterStatesUsingMultipleConjunctiveComparisonsOnMetadataValuesAndLastUpdatedTime() {
        val count = 20
        val half = count / 2
        val keyIndexRange = 1..count
        persistStateEntities(
            (keyIndexRange),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i, "boolean": ${i % 2 == 0}, "string": "random_$i" }""" }
        )
        val (halfTime, finishTime) = getIntervalBetweenEntities(
            buildStateKey(keyIndexRange.elementAt(half)),
            buildStateKey(keyIndexRange.last)
        )

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAll(
                IntervalFilter(Instant.EPOCH, halfTime),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).hasSize(1)

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAll(
                IntervalFilter(finishTime, finishTime.plusSeconds(60)),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).isEmpty()

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAll(
                IntervalFilter(halfTime, finishTime),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 10),
                    MetadataFilter("boolean", Operation.Equals, true),
                )
            )
        ).hasSize(half / 2)

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAll(
                IntervalFilter(Instant.EPOCH, finishTime.plusSeconds(60)),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 1),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "non_existing_value"),
                )
            )
        ).isEmpty()

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAll(
                IntervalFilter(halfTime, finishTime),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 10),
                    MetadataFilter("number", Operation.LesserThan, 50),
                    MetadataFilter("string", Operation.NotEquals, "non_existing_value"),
                )
            )
        ).hasSize(half)
    }

    @Test
    @DisplayName(value = "can filter states using multiple disjunctive comparisons on metadata values and last updated time")
    fun canFilterStatesUsingMultipleDisjunctiveComparisonsOnMetadataValuesAndLastUpdatedTime() {
        val count = 20
        val half = count / 2
        val keyIndexRange = 1..count
        persistStateEntities(
            (keyIndexRange),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i, "boolean": ${i % 2 == 0}, "string": "random_$i" }""" }
        )
        val (halfTime, finishTime) = getIntervalBetweenEntities(
            buildStateKey(keyIndexRange.elementAt(half)),
            buildStateKey(keyIndexRange.last)
        )

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAny(
                IntervalFilter(Instant.EPOCH, halfTime),
                listOf(
                    MetadataFilter("number", Operation.Equals, 5),
                    MetadataFilter("number", Operation.Equals, 7),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).hasSize(3)

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAny(
                IntervalFilter(finishTime, finishTime.plusSeconds(60)),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).hasSize(1)

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAny(
                IntervalFilter(halfTime, finishTime),
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 1),
                    MetadataFilter("boolean", Operation.Equals, true),
                )
            )
        ).hasSize(half)

        assertThat(
            stateManager.findUpdatedBetweenWithMetadataMatchingAny(
                IntervalFilter(Instant.EPOCH, finishTime.plusSeconds(60)),
                listOf(
                    MetadataFilter("number", Operation.Equals, 25),
                    MetadataFilter("string", Operation.Equals, "non_existing_value"),
                )
            )
        ).isEmpty()
    }

    @AfterEach
    fun tearDown() {
        cleanStates()
    }

    @AfterAll
    fun cleanUp() {
        dataSource.close()
    }
}
