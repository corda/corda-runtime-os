package net.corda.libs.statemanager.impl.tests

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.model.v1.CREATE_STATE_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.KEY_ID
import net.corda.libs.statemanager.impl.model.v1.METADATA_ID
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.model.v1.StateManagerEntities
import net.corda.libs.statemanager.impl.model.v1.VALUE_ID
import net.corda.libs.statemanager.impl.model.v1.VERSION_ID
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import javax.persistence.PersistenceException
import kotlin.concurrent.thread

// TODO-[CORE-16663]: make database provider pluggable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateManagerIntegrationTest {

    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("state_manager_db")

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
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    private val uniqueId = UUID.randomUUID()
    private val objectMapper = ObjectMapper()
    private val entityManagerFactoryFactory = EntityManagerFactoryFactoryImpl().create(
        "state_manager_test",
        StateManagerEntities.classes.toList(),
        dbConfig
    )
    private val stateManager: StateManager = StateManagerImpl(StateRepositoryImpl(), entityManagerFactoryFactory)

    private fun ObjectMapper.toMetadata(metadata: String) =
        this.readValue(metadata, object : TypeReference<Metadata>() {})

    private fun cleanStates() = entityManagerFactoryFactory.createEntityManager().transaction {
        it.createNativeQuery("DELETE FROM state s WHERE s.key LIKE '%$uniqueId%'").executeUpdate()
        it.flush()
    }

    @BeforeEach
    fun setUp() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        cleanStates()
    }

    private fun buildStateKey(index: Int, uniqueId: UUID) = "key_$index-$uniqueId"

    private fun persistStateEntities(
        uniqueId: UUID,
        indexRange: IntProgression,
        version: (index: Int, key: String) -> Int,
        stateContent: (index: Int, key: String) -> String,
        metadataContent: (index: Int, key: String) -> String,
    ) = indexRange.forEach { i ->
        entityManagerFactoryFactory.createEntityManager().transaction {
            val key = buildStateKey(i, uniqueId)
            val stateEntity =
                StateEntity(key, stateContent(i, key).toByteArray(), metadataContent(i, key), version(i, key))

            it.createNamedQuery(CREATE_STATE_QUERY_NAME.trimIndent())
                .setParameter(KEY_ID, stateEntity.key)
                .setParameter(VALUE_ID, stateEntity.value)
                .setParameter(VERSION_ID, stateEntity.version)
                .setParameter(METADATA_ID, stateEntity.metadata)
                .executeUpdate()

            it.flush()
        }
    }

    private fun softlyAssertPersistedStateEntities(
        uniqueId: UUID,
        indexRange: IntProgression,
        version: (index: Int, key: String) -> Int,
        stateContent: (index: Int, key: String) -> String,
        metadataContent: (index: Int, key: String) -> Metadata,
    ) = entityManagerFactoryFactory.createEntityManager().use { em ->
        indexRange.forEach { i ->
            val key = buildStateKey(i, uniqueId)
            val loadedEntity = em.find(StateEntity::class.java, key)

            assertSoftly {
                it.assertThat(loadedEntity.key).isEqualTo(key)
                it.assertThat(loadedEntity.modifiedTime).isNotNull
                it.assertThat(loadedEntity.version).isEqualTo(version(i, key))
                it.assertThat(loadedEntity.value).isEqualTo((stateContent(i, key).toByteArray()))
                it.assertThat(objectMapper.toMetadata(loadedEntity.metadata))
                    .containsExactlyInAnyOrderEntriesOf(metadataContent(i, key))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canCreateBasicStates(stateCount: Int) {
        val states = mutableSetOf<State>()
        for (i in 1..stateCount) {
            states.add(State(buildStateKey(i, uniqueId), "simpleState_$i".toByteArray()))
        }

        assertThat(stateManager.create(states)).isEmpty()
        softlyAssertPersistedStateEntities(
            uniqueId,
            (1..stateCount),
            { _, _ -> -1 },
            { i, _ -> "simpleState_$i" },
            { _, _ -> metadata() }
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canCreateStatesWithCustomMetadata(stateCount: Int) {
        val states = mutableSetOf<State>()
        for (i in 1..stateCount) {
            states.add(
                State(
                    buildStateKey(i, uniqueId),
                    "customState_$i".toByteArray(),
                    metadata = metadata("key1" to "value$i", "key2" to i)
                )
            )
        }

        assertThat(stateManager.create(states)).isEmpty()
        softlyAssertPersistedStateEntities(
            uniqueId,
            (1..stateCount),
            { _, _ -> -1 },
            { i, _ -> "customState_$i" },
            { i, _ -> metadata("key1" to "value$i", "key2" to i) }
        )
    }

    @Test
    fun failuresWhenPersistingSomeStatesDoesNotHaltTheEntireBatch() {
        val failedSates = 5
        val totalStates = 15
        persistStateEntities(
            uniqueId,
            (1..failedSates),
            { _, _ -> -1 },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )
        val states = mutableSetOf<State>()
        for (i in 1..totalStates) {
            states.add(State(buildStateKey(i, uniqueId), "newState_$i".toByteArray()))
        }

        val failures = stateManager.create(states)
        assertThat(failures).hasSize(failedSates)
        for (i in 1..failedSates) {
            assertThat(failures[buildStateKey(i, uniqueId)]).isInstanceOf(PersistenceException::class.java)
        }
        softlyAssertPersistedStateEntities(
            uniqueId,
            (failedSates + 1..totalStates),
            { _, _ -> -1 },
            { i, _ -> "newState_$i" },
            { _, _ -> metadata() }
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canRetrieveStatesByKey(stateCount: Int) {
        persistStateEntities(
            uniqueId,
            (1..stateCount),
            { _, _ -> -1 },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val states = stateManager.get((1..stateCount).map { buildStateKey(it, uniqueId) }.toSet())
        assertThat(states.size).isEqualTo(stateCount)
        for (i in 1..stateCount) {
            val key = buildStateKey(i, uniqueId)
            val loadedState = states[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.value).isEqualTo("existingState_$i".toByteArray())
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(-1)
                it.assertThat(loadedState.metadata)
                    .containsExactlyInAnyOrderEntriesOf(mutableMapOf("k1" to "v$i", "k2" to i))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canUpdateExistingStates(stateCount: Int) {
        persistStateEntities(
            uniqueId,
            (1..stateCount),
            { i, _ -> i },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )
        val statesToUpdate = mutableSetOf<State>()
        for (i in 1..stateCount) {
            statesToUpdate.add(
                State(buildStateKey(i, uniqueId), "state_$i$i".toByteArray(), i, metadata("1yek" to "1eulav"))
            )
        }

        val failedUpdates = stateManager.update(statesToUpdate)
        assertThat(failedUpdates).isEmpty()
        softlyAssertPersistedStateEntities(
            uniqueId,
            (1..stateCount),
            { i, _ -> i + 1 },
            { i, _ -> "state_$i$i" },
            { _, _ -> metadata("1yek" to "1eulav") }
        )
    }

    @Test
    fun optimisticLockingChecksForConcurrentUpdatesDoNotHaltTheEntireBatch() {
        val totalCount = 20
        persistStateEntities(
            uniqueId,
            (1..totalCount),
            { _, _ -> 0 },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val allKeys = (1..totalCount).map { buildStateKey(it, uniqueId) }
        val conflictingKeys = (1..totalCount).filter { it % 2 == 0 }.map { buildStateKey(it, uniqueId) }
        val persistedStates = stateManager.get(allKeys)

        val latch = CountDownLatch(1)
        val updater1 = thread {
            val statesToUpdateFirstThread = mutableListOf<State>()
            conflictingKeys.forEach {
                val state = persistedStates[it]!!
                statesToUpdateFirstThread.add(
                    State(state.key, "u1_$it".toByteArray(), state.version, metadata("u1" to it))
                )
            }

            assertThat(stateManager.update(statesToUpdateFirstThread)).isEmpty()
            latch.countDown()
        }

        val updater2 = thread {
            val statesToUpdateSecondThread = mutableListOf<State>()
            allKeys.forEach {
                val state = persistedStates[it]!!
                statesToUpdateSecondThread.add(
                    State(state.key, "u2_$it".toByteArray(), state.version, metadata("u2" to it))
                )
            }

            latch.await()
            val failedUpdates = stateManager.update(statesToUpdateSecondThread)
            assertThat(failedUpdates).containsOnlyKeys(conflictingKeys)
            assertThat(failedUpdates).containsValues(
                *statesToUpdateSecondThread.filter { conflictingKeys.contains(it.key) }.toTypedArray()
            )
        }

        updater1.join()
        updater2.join()

        softlyAssertPersistedStateEntities(
            uniqueId,
            (1..totalCount),
            { _, _ -> 1 },
            { _, key -> if (conflictingKeys.contains(key)) "u1_$key" else "u2_$key" },
            { _, key -> if (conflictingKeys.contains(key)) metadata("u1" to key) else metadata("u2" to key) },
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canDeleteExistingStates(stateCount: Int) {
        persistStateEntities(
            uniqueId,
            (1..stateCount),
            { i, _ -> i },
            { i, _ -> "stateToDelete_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val statesToDelete = mutableSetOf<State>()
        for (i in 1..stateCount) {
            statesToDelete.add(State(buildStateKey(i, uniqueId), "".toByteArray(), i))
        }

        assertThat(stateManager.get(statesToDelete.map { it.key })).hasSize(stateCount)
        stateManager.delete(statesToDelete)
        assertThat(stateManager.get(statesToDelete.map { it.key })).isEmpty()
    }

    @Test
    fun optimisticLockingCheckForConcurrentDeletesDoesNotHaltTheEntireBatch() {
        val totalCount = 20
        persistStateEntities(
            uniqueId,
            (1..totalCount),
            { _, _ -> 0 },
            { i, _ -> "existingState_$i" },
            { i, _ -> """{"k1": "v$i", "k2": $i}""" }
        )

        val allKeys = (1..totalCount).map { buildStateKey(it, uniqueId) }
        val conflictingKeys = (1..totalCount).filter { it % 2 == 0 }.map { buildStateKey(it, uniqueId) }
        val persistedStates = stateManager.get(allKeys)

        val latch = CountDownLatch(1)
        val updater = thread {
            val statesToUpdate = mutableListOf<State>()
            conflictingKeys.forEach {
                val state = persistedStates[it]!!
                statesToUpdate.add(
                    State(state.key, "u1_$it".toByteArray(), state.version, metadata("u1" to it))
                )
            }

            assertThat(stateManager.update(statesToUpdate)).isEmpty()
            latch.countDown()
        }

        val deleter = thread {
            val statesToDelete = mutableListOf<State>()
            allKeys.forEach {
                val state = persistedStates[it]!!
                statesToDelete.add(State(state.key, "delete".toByteArray(), state.version))
            }

            latch.await()
            val failedDeletes = stateManager.delete(statesToDelete)
            assertThat(failedDeletes).containsOnlyKeys(conflictingKeys)
            assertThat(failedDeletes).containsValues(
                *statesToDelete.filter { conflictingKeys.contains(it.key) }.toTypedArray()
            )
        }

        updater.join()
        deleter.join()

        softlyAssertPersistedStateEntities(
            uniqueId,
            (2..totalCount step 2),
            { _, _ -> 1 },
            { _, key -> "u1_$key" },
            { _, key -> metadata("u1" to key) },
        )
    }

    @Test
    fun canFilterStatesByLastUpdatedTime() {
        val count = 10
        val startTime = Instant.now()
        persistStateEntities(
            uniqueId,
            (1..count),
            { _, _ -> -1 },
            { i, _ -> "state_$i" },
            { _, _ -> "{}" }
        )
        val finishTime = Instant.now()

        val filteredStates = stateManager.getUpdatedBetween(startTime, finishTime)
        assertThat(filteredStates).hasSize(count)

        for (i in 1..count) {
            val key = buildStateKey(i, uniqueId)
            val loadedState = filteredStates[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.value).isEqualTo("state_$i".toByteArray())
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(-1)
                it.assertThat(loadedState.metadata).containsExactlyInAnyOrderEntriesOf(emptyMap())
            }
        }
    }

    @Test
    fun canFilterStatesUsingSimpleComparisonsOnMetadataValues() {
        val count = 20
        persistStateEntities(
            uniqueId,
            (1..count),
            { _, _ -> 0 },
            { i, _ -> "state_$i" },
            { i, _ -> """{ "number": $i, "boolean": ${i % 2 == 0}, "string": "random_$i" }""" }
        )

        // Numeric
        assertThat(stateManager.find("number", Operation.Equals, count)).hasSize(1)
        assertThat(stateManager.find("number", Operation.NotEquals, count)).hasSize(count - 1)
        assertThat(stateManager.find("number", Operation.GreaterThan, count)).isEmpty()
        assertThat(stateManager.find("number", Operation.LesserThan, count)).hasSize(count - 1)

        // String
        assertThat(stateManager.find("string", Operation.Equals, "random_$count")).hasSize(1)
        assertThat(stateManager.find("string", Operation.NotEquals, "random")).hasSize(count)
        assertThat(stateManager.find("string", Operation.GreaterThan, "random_1")).hasSize(count - 1)
        assertThat(stateManager.find("string", Operation.LesserThan, "random_1")).isEmpty()

        // Booleans
        assertThat(stateManager.find("boolean", Operation.Equals, true)).hasSize(count / 2)
        assertThat(stateManager.find("boolean", Operation.NotEquals, true)).hasSize(count / 2)
        assertThat(stateManager.find("boolean", Operation.GreaterThan, false)).hasSize(count / 2)
        assertThat(stateManager.find("boolean", Operation.LesserThan, false)).isEmpty()
    }

    @AfterEach
    fun tearDown() {
        cleanStates()
    }

    @AfterAll
    fun cleanUp() {
        entityManagerFactoryFactory.close()
    }
}
