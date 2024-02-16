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
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder
import net.corda.libs.statemanager.impl.metrics.MetricsRecorderImpl
import net.corda.libs.statemanager.impl.model.v1.resultSetAsStateCollection
import net.corda.libs.statemanager.impl.repository.impl.PostgresQueryProvider
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.libs.statemanager.impl.tests.MultiThreadedTestHelper.runMultiThreadedOptimisticLockingTest
import net.corda.libs.statemanager.impl.tests.MultiThreadedTestHelper.updateStateObjects
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.metrics.CordaMetrics
import net.corda.test.util.metrics.CORDA_METRICS_LOCK
import net.corda.test.util.metrics.EachTestCordaMetrics
import net.corda.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.math.abs

// TODO-[CORE-16663]: make database provider pluggable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceLock(CORDA_METRICS_LOCK)
class StateManagerIntegrationTest {
    private val objectMapper = ObjectMapper()
    private val testUniqueId = UUID.randomUUID()
    private val queryProvider = PostgresQueryProvider()
    private val maxConcurrentThreadJdbcConnections = 10
    private val dataSource = DbUtils.createDataSource(maximumPoolSize = maxConcurrentThreadJdbcConnections)

    @RegisterExtension
    @Suppress("unused")
    private val metrics = EachTestCordaMetrics(testUniqueId.toString())

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

    private val stateManager: StateManager = StateManagerImpl(
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>(),
        dataSource = dataSource,
        stateRepository = StateRepositoryImpl(queryProvider),
        metricsRecorder = MetricsRecorderImpl()
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
            val value = stateContent(i, key).toByteArray()
            val versionNumber = version(i, key)
            val metadata = metadataContent(i, key)

            connection.prepareStatement(queryProvider.createStates(1)).use {
                it.setString(1, key)
                it.setBytes(2, value)
                it.setInt(3, versionNumber)
                it.setString(4, metadata)
                it.execute()
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
                    it.executeQuery().resultSetAsStateCollection(objectMapper)
                }.elementAt(0)

            assertSoftly {
                it.assertThat(loadedEntity.key).isEqualTo(key)
                it.assertThat(loadedEntity.modifiedTime).isNotNull
                it.assertThat(loadedEntity.version).isEqualTo(version(i, key))
                it.assertThat(loadedEntity.value).isEqualTo((stateContent(i, key).toByteArray()))
                it.assertThat(loadedEntity.metadata)
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
                    it.executeQuery().resultSetAsStateCollection(objectMapper)
                }.sortedBy {
                    it.modifiedTime
                }

            Pair(
                loadedEntities.elementAt(0).modifiedTime,
                loadedEntities.elementAt(1).modifiedTime
            )
        }
    }

    private fun verifyHistogramSnapshotValues(
        operation: MetricsRecorder.OperationType,
        samples: Long = 1,
        minTotalTime: Double = 0.0
    ) {
        val meter = CordaMetrics.registry
            .find("corda.${CordaMetrics.Metric.StateManger.ExecutionTime.metricsName}")
            .tag(CordaMetrics.Tag.WorkerType.value, testUniqueId.toString())
            .tag(CordaMetrics.Tag.OperationName.value, operation.toString())

        val timer = meter.timer()
        assertThat(timer)
            .withFailMessage("Meter for StateManager operation $operation does not exist")
            .isNotNull

        val metricId = timer!!.id
        timer.takeSnapshot().also {
            val count = it.count()
            assertThat(count)
                .withFailMessage("Expected count for $metricId to have count $samples but was $count")
                .isEqualTo(samples)

            val totalTime = it.total()
            assertThat(totalTime)
                .withFailMessage("Expected totalTime for $metricId to be greater or equal than $minTotalTime but was $totalTime")
                .isGreaterThanOrEqualTo(minTotalTime)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.CREATE, 1)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.CREATE, 1)
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
            assertThat(failures).contains(buildStateKey(i))
        }
        softlyAssertPersistedStateEntities(
            (failedSates + 1..totalStates),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "newState_$i" },
            { _, _ -> metadata() }
        )

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.CREATE, 1)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 1)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.UPDATE, 1)
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
                it.assertThat(state).isNotNull
                // update A has already bumped the version by 1, causing B's state update to fail
                it.assertThat(state!!.version).isEqualTo(statesToUpdateB[state.key]!!.version + 1)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 1)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.UPDATE, 2)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 1)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.UPDATE, numThreads.toLong())
    }

    @Suppress("SpreadOperator")
    @Test
    fun `optimistic locking ensures no exceptions when double deletes across threads`() {
        val totalStates = 100
        val numThreads = maxConcurrentThreadJdbcConnections
        val sharedStatesPerThread = 5

        persistStateEntities(
            (1..totalStates),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"id": "$i"}""" }
        )

        val allKeys = (1..totalStates).map { buildStateKey(it) }
        val allStatesInTest = stateManager.get(allKeys).values.toList()

        val latch = CountDownLatch(numThreads)
        val threadResults = runMultiThreadedOptimisticLockingTest(
            allStatesInTest,
            numThreads,
            sharedStatesPerThread
        ) { threadIndex, stateGroup ->
            // Thread will attempt to update its own assigned states, and delete the states overlapping into the next group.
            // This means every thread will have race condition with the next thread.
            val statesToUpdate = updateStateObjects(stateGroup.assignedStates, testUniqueId, threadIndex)
            val statesToDelete = stateGroup.overlappingStates

            latch.countDown()
            latch.await()
            val failedUpdates = stateManager.update(statesToUpdate).map { it.key }
            val failedDeletes = stateManager.delete(statesToDelete).map { it.key }
            MultiThreadedTestHelper.FailedKeysSummary(failedUpdates, failedDeletes)
        }

        val allFailedUpdates = threadResults.map { it.failedKeysSummary.failedUpdates }.flatten()
        val allFailedDeletes = threadResults.map { it.failedKeysSummary.failedDeletes }.flatten()
        val expectedFailedKeys = threadResults.map { it.assignedStateGrouping.overlappingStates }.flatten().map { it.key }

        // if a thread tries to update a state that was already deleted, it gets that key back as a "failed key", associated with null.
        // if a thread tries to delete a state that was already updated, it gets that key back as a "failed key".
        // we expect to see a failed key for every overlapping state, because in the race between two threads only
        // one can update or delete it.
        assertThat(allFailedUpdates + allFailedDeletes)
            .containsExactlyInAnyOrder(*expectedFailedKeys.toTypedArray())
            .withFailMessage("Expected one failure for every state shared between another thread")

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 1)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.UPDATE, numThreads.toLong())
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.DELETE, numThreads.toLong())
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 2)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.DELETE, 1)
    }

    @Test
    fun `delete returns empty collection of keys if the states were never present`() {
        val statesToDelete = (1..20).map {
            State(buildStateKey(it), "".toByteArray(), it)
        }
        // Double check that the states we're requesting do not exist.
        assertThat(stateManager.get(statesToDelete.map { it.key })).isEmpty()
        val failed = stateManager.delete(statesToDelete)
        assertThat(failed).isEmpty()

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 1)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.DELETE, 1)
    }

    @Test
    fun `batch operations perform all requested changes when executed`() {
        val statesToCreate = (1..5).map {
            State(buildStateKey(it), "".toByteArray())
        }
        persistStateEntities(
            (6..15),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { _, _ -> "{}" }
        )
        softlyAssertPersistedStateEntities(
            (6..15),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "existingState_$i" },
            { _, _ -> metadata() }
        )
        val statesToUpdate = (6..10).map {
            State(buildStateKey(it), "".toByteArray(), version = State.VERSION_INITIAL_VALUE)
        }
        val statesToDelete = (11..15).map {
            State(buildStateKey(it), "".toByteArray(), version = State.VERSION_INITIAL_VALUE)
        }
        val batch = stateManager.createOperationGroup()
        val failures = batch
            .create(statesToCreate)
            .update(statesToUpdate)
            .delete(statesToDelete)
            .execute()
        assertThat(failures).isEmpty()
        softlyAssertPersistedStateEntities(
            (1..5),
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { _, _ -> "" },
            { _, _ -> metadata() }
        )
        softlyAssertPersistedStateEntities(
            (6..10),
            { _, _ -> 1 },
            { _, _ -> "" },
            { _, _ -> metadata() }
        )
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.GET, 1)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.UPDATE, 1)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.DELETE, 1)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 2)
        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.UPDATE, 1)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 12)
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

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).hasSize(1)

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "non_existing_value"),
                )
            )
        ).isEmpty()

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 0),
                    MetadataFilter("boolean", Operation.Equals, true),
                )
            )
        ).hasSize(count / 2)

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.NotEquals, 0),
                    MetadataFilter("string", Operation.Equals, "non_existing_key"),
                )
            )
        ).isEmpty()

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 4)
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

        assertThat(
            stateManager.findByMetadataMatchingAny(
                listOf(
                    MetadataFilter("number", Operation.Equals, 5),
                    MetadataFilter("number", Operation.Equals, 7),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).hasSize(3)

        assertThat(
            stateManager.findByMetadataMatchingAny(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                )
            )
        ).hasSize(count)

        assertThat(
            stateManager.findByMetadataMatchingAny(
                listOf(
                    MetadataFilter("boolean", Operation.Equals, false),
                    MetadataFilter("boolean", Operation.Equals, true),
                )
            )
        ).hasSize(count)

        assertThat(
            stateManager.findByMetadataMatchingAny(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 20),
                    MetadataFilter("boolean", Operation.Equals, true),
                )
            )
        ).hasSize(count / 2)

        assertThat(
            stateManager.findByMetadataMatchingAny(
                listOf(
                    MetadataFilter("number", Operation.Equals, 0),
                    MetadataFilter("string", Operation.Equals, "non_existing_key"),
                )
            )
        ).isEmpty()

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 5)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 5)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 5)
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

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 4)
    }

    private fun withTimeZone(timeZone: TimeZone, block: () -> Unit) {
        val defaultTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(timeZone)
            return block()
        } finally {
            TimeZone.setDefault(defaultTimeZone)
        }
    }

    @Test
    @DisplayName(value = "can filter states by last updated time independently of the default time zone")
    fun canFilterStatesByLastUpdatedTimeIndependentlyOfTheDefaultTimeZone() {
        val count = 10
        val keyIndexRange = 1..count
        persistStateEntities(
            keyIndexRange,
            { _, _ -> State.VERSION_INITIAL_VALUE },
            { i, _ -> "state_$i" },
            { _, _ -> """{ "boolean": true }""" }
        )

        // Calculate the TimeZone that is "furthest" away in time
        val defaultOffset = TimeZone.getDefault().rawOffset / (1000 * 60 * 60)
        val furthestTimeZone = TimeZone.getTimeZone(
            TimeZone.getAvailableIDs().maxByOrNull { id ->
                val zone = TimeZone.getTimeZone(id)
                val zoneOffset = zone.rawOffset / (1000 * 60 * 60)
                abs(zoneOffset - defaultOffset)
            }
        )

        withTimeZone(furthestTimeZone) {
            assertThat(
                stateManager.updatedBetween(
                    IntervalFilter(
                        Instant.now().minusSeconds(30.minutes.toSeconds()),
                        Instant.now().plusSeconds(30.minutes.toSeconds()),
                    )
                )
            ).hasSize(count)
        }

        withTimeZone(furthestTimeZone) {
            assertThat(
                stateManager.findUpdatedBetweenWithMetadataMatchingAll(
                    IntervalFilter(
                        Instant.now().minusSeconds(30.minutes.toSeconds()),
                        Instant.now().plusSeconds(30.minutes.toSeconds()),
                    ),
                    listOf(MetadataFilter("boolean", Operation.Equals, true))
                )
            ).hasSize(count)
        }

        withTimeZone(furthestTimeZone) {
            assertThat(
                stateManager.findUpdatedBetweenWithMetadataMatchingAny(
                    IntervalFilter(
                        Instant.now().minusSeconds(30.minutes.toSeconds()),
                        Instant.now().plusSeconds(30.minutes.toSeconds()),
                    ),
                    listOf(MetadataFilter("boolean", Operation.Equals, true))
                )
            ).hasSize(count)
        }

        verifyHistogramSnapshotValues(MetricsRecorder.OperationType.FIND, 3)
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
