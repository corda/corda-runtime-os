package net.corda.libs.statemanager.impl.tests

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.model.v1.CREATE_STATE_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.KEY_ID
import net.corda.libs.statemanager.impl.model.v1.METADATA_ID
import net.corda.libs.statemanager.impl.model.v1.STATE_ID
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.model.v1.StateManagerEntities
import net.corda.libs.statemanager.impl.model.v1.VERSION_ID
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateManagerIntegrationTest {

    private val objectMapper = ObjectMapper()

    private fun ObjectMapper.toMetadata(metadata: String) =
        this.readValue(metadata, object : TypeReference<Metadata<Any>>() {})

    private val stringSerializer = object : CordaAvroSerializer<String> {
        override fun serialize(data: String): ByteArray = data.toByteArray()
    }

    private val stringDeserializer = object : CordaAvroDeserializer<String> {
        override fun deserialize(data: ByteArray): String = String(data)
    }

    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<String>() } doReturn stringSerializer
        on { createAvroDeserializer(any(), eq(String::class.java)) } doReturn stringDeserializer
    }

    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("state_manager_db")

    private val entityManagerFactoryFactory = EntityManagerFactoryFactoryImpl().create(
        "state_manager_test",
        StateManagerEntities.classes.toList(),
        dbConfig
    )

    private val stateManager: StateManager = StateManagerImpl(
        StateRepositoryImpl(),
        entityManagerFactoryFactory,
        cordaAvroSerializationFactory
    )

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

    @BeforeEach
    fun setUp() {
        // TODO-[CORE-16663]: make the database provider pluggable.
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
    }

    @AfterAll
    fun cleanUp() {
        entityManagerFactoryFactory.close()
    }

    private fun key(index: Int, uniqueId: UUID) = "key_$index-$uniqueId"

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canCreateBasicStates(stateCount: Int) {
        val uniqueId = UUID.randomUUID()
        val states = mutableSetOf<State<String>>()
        for (i in 1..stateCount) {
            states.add(State("simpleState_$i", key(i, uniqueId)))
        }
        stateManager.create(String::class.java, states)

        entityManagerFactoryFactory.createEntityManager().use { em ->
            for (i in 1..stateCount) {
                val key = key(i, uniqueId)
                val loadedEntity = em.find(StateEntity::class.java, key)

                assertSoftly {
                    it.assertThat(loadedEntity.modifiedTime).isNotNull
                    it.assertThat(loadedEntity.key).isEqualTo(key)
                    it.assertThat(loadedEntity.version).isEqualTo(-1)
                    it.assertThat(stringDeserializer.deserialize(loadedEntity.state)).isEqualTo("simpleState_$i")
                    it.assertThat(objectMapper.toMetadata(loadedEntity.metadata))
                        .containsExactlyInAnyOrderEntriesOf(emptyMap())
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canCreateStatesWithCustomMetadata(stateCount: Int) {
        val uniqueId = UUID.randomUUID()
        val states = mutableSetOf<State<String>>()
        for (i in 1..stateCount) {
            states.add(State("customState_$i", key(i, uniqueId), metadata("key1" to "value$i", "key2" to i)))
        }
        stateManager.create(String::class.java, states)

        entityManagerFactoryFactory.createEntityManager().use { em ->
            for (i in 1..stateCount) {
                val key = key(i, uniqueId)
                val loadedEntity = em.find(StateEntity::class.java, key)

                assertSoftly {
                    it.assertThat(loadedEntity.modifiedTime).isNotNull
                    it.assertThat(loadedEntity.key).isEqualTo(key)
                    it.assertThat(loadedEntity.version).isEqualTo(-1)
                    it.assertThat(stringDeserializer.deserialize(loadedEntity.state)).isEqualTo("customState_$i")
                    it.assertThat(objectMapper.toMetadata(loadedEntity.metadata))
                        .containsExactlyInAnyOrderEntriesOf(mapOf("key1" to "value$i", "key2" to i))
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canRetrieveStatesByKey(stateCount: Int) {
        val uniqueId = UUID.randomUUID()
        entityManagerFactoryFactory.createEntityManager().transaction {
            for (i in 1..stateCount) {
                val stateEntity =
                    StateEntity(key(i, uniqueId), "state_$i".toByteArray(), """{"k1": "v$i", "k2": $i}""")

                it.createNamedQuery(CREATE_STATE_QUERY_NAME.trimIndent())
                    .setParameter(KEY_ID, stateEntity.key)
                    .setParameter(STATE_ID, stateEntity.state)
                    .setParameter(VERSION_ID, stateEntity.version)
                    .setParameter(METADATA_ID, stateEntity.metadata)
                    .executeUpdate()
            }

            it.flush()
        }

        val states = stateManager.get(String::class.java, (1..stateCount).map { key(it, uniqueId) }.toSet())
        assertThat(states.size).isEqualTo(stateCount)

        for (i in 1..stateCount) {
            val key = key(i, uniqueId)
            val loadedState = states[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.state).isEqualTo("state_$i")
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(-1)
                it.assertThat(loadedState.metadata)
                    .containsExactlyInAnyOrderEntriesOf(mutableMapOf("k1" to "v$i", "k2" to i))
            }
        }
    }

    @Test
    fun canCreateAndRetrieveTheSameState() {
        val key = UUID.randomUUID().toString()
        val state = State("customState", key, metadata("key1" to "value1", "key2" to 23), 10, Instant.now())
        stateManager.create(String::class.java, setOf(state))

        val retrievedState = stateManager.get(String::class.java, setOf(key))[key]
        assertThat(retrievedState).isNotNull
        retrievedState!!

        assertSoftly {
            it.assertThat(retrievedState.key).isEqualTo(state.key)
            it.assertThat(retrievedState.state).isEqualTo(state.state)
            it.assertThat(retrievedState.version).isEqualTo(state.version)
            it.assertThat(retrievedState.modifiedTime).isAfter(state.modifiedTime)
            it.assertThat(retrievedState.metadata).containsExactlyInAnyOrderEntriesOf(state.metadata)
        }
    }

    @Test
    fun canNotCreateTwoStatesWithTheSameKey() {
        val key = UUID.randomUUID().toString()
        val state = State("simpleState", key)
        stateManager.create(String::class.java, setOf(state))

        assertThatThrownBy {
            stateManager.create(String::class.java, setOf(state))
        }.isInstanceOf(PersistenceException::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canUpdateExistingStates(stateCount: Int) {
        val uniqueId = UUID.randomUUID()
        entityManagerFactoryFactory.createEntityManager().transaction {
            for (i in 1..stateCount) {
                val stateEntity =
                    StateEntity(key(i, uniqueId), "state_$i".toByteArray(), """{"k1": "v$i", "k2": $i}""")

                it.createNamedQuery(CREATE_STATE_QUERY_NAME.trimIndent())
                    .setParameter(KEY_ID, stateEntity.key)
                    .setParameter(STATE_ID, stateEntity.state)
                    .setParameter(VERSION_ID, stateEntity.version)
                    .setParameter(METADATA_ID, stateEntity.metadata)
                    .executeUpdate()
            }

            it.flush()
        }

        val updatedStates = mutableSetOf<State<String>>()
        for (i in 1..stateCount) {
            updatedStates.add(
                State("state_$i$i", key(i, uniqueId), metadata("1yek" to "1eulav"))
            )
        }
        stateManager.update(String::class.java, updatedStates)

        val states = stateManager.get(String::class.java, (1..stateCount).map { key(it, uniqueId) }.toSet())
        assertThat(states.size).isEqualTo(stateCount)

        for (i in 1..stateCount) {
            val key = key(i, uniqueId)
            val loadedState = states[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.state).isEqualTo("state_$i$i")
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.metadata).containsExactlyInAnyOrderEntriesOf(mutableMapOf("1yek" to "1eulav"))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun canDeleteExistingStates(stateCount: Int) {
        val uniqueId = UUID.randomUUID()
        entityManagerFactoryFactory.createEntityManager().transaction {
            for (i in 1..stateCount) {
                val stateEntity =
                    StateEntity(key(i, uniqueId), "state_$i".toByteArray(), """{"k1": "v$i", "k2": $i}""")

                it.createNamedQuery(CREATE_STATE_QUERY_NAME.trimIndent())
                    .setParameter(KEY_ID, stateEntity.key)
                    .setParameter(STATE_ID, stateEntity.state)
                    .setParameter(VERSION_ID, stateEntity.version)
                    .setParameter(METADATA_ID, stateEntity.metadata)
                    .executeUpdate()
            }

            it.flush()
        }

        val keys = (1..stateCount).map { key(it, uniqueId) }.toSet()
        assertThat(stateManager.get(String::class.java, keys)).hasSize(stateCount)
        stateManager.delete(String::class.java, keys)
        assertThat(stateManager.get(String::class.java, keys)).isEmpty()
    }

    @Test
    fun canQueryStatesByLastModifiedTime() {
        val count = 10
        val uniqueId = UUID.randomUUID()

        // Different transactions to get different timestamps
        for (i in 1..count) {
            val stateEntity =
                StateEntity(key(i, uniqueId), "state_$i".toByteArray(), "{}")
            entityManagerFactoryFactory.createEntityManager().transaction {
                it.createNamedQuery(CREATE_STATE_QUERY_NAME.trimIndent())
                    .setParameter(KEY_ID, stateEntity.key)
                    .setParameter(STATE_ID, stateEntity.state)
                    .setParameter(VERSION_ID, stateEntity.version)
                    .setParameter(METADATA_ID, stateEntity.metadata)
                    .executeUpdate()
            }
        }

        // Timestamps are generated on the database and timezones might differ, so use the values from the first and last created states
        val startKey = key(1, uniqueId)
        val finishKey = key(count, uniqueId)
        val times = stateManager.get(String::class.java, setOf(startKey, finishKey))
        val retrievedStates = stateManager.getUpdatedBetween(
            String::class.java,
            times[startKey]!!.modifiedTime,
            times[finishKey]!!.modifiedTime
        )
        assertThat(retrievedStates).hasSize(count)

        for (i in 1..count) {
            val key = key(i, uniqueId)
            val loadedState = retrievedStates[key]
            assertThat(loadedState).isNotNull
            loadedState!!

            assertSoftly {
                it.assertThat(loadedState.modifiedTime).isNotNull
                it.assertThat(loadedState.state).isEqualTo("state_$i")
                it.assertThat(loadedState.key).isEqualTo(key)
                it.assertThat(loadedState.version).isEqualTo(-1)
                it.assertThat(loadedState.metadata).containsExactlyInAnyOrderEntriesOf(emptyMap())
            }
        }
    }
}
