package net.corda.libs.statemanager.impl.tests

import java.time.Instant
import java.util.UUID
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.statemanager.impl.dto.StateDto
import net.corda.libs.statemanager.impl.model.v1_0.StateManagerEntities
import net.corda.libs.statemanager.impl.repository.impl.StateManagerRepositoryImpl
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateManagerIntegrationTest {
    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("statemanager_db")
    private val emf = EntityManagerFactoryFactoryImpl().create(
        "test_unit",
        StateManagerEntities.classes.toList(),
        dbConfig
    )
    private val stateManagerRepository = StateManagerRepositoryImpl()

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

    @AfterAll
    fun cleanUp() {
        emf.close()
    }

    @Test
    fun `can persist state using state manager`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val key = UUID.randomUUID().toString()
        val jsonb = """{"metadatakey_$key": "metadatavalue_$key"}"""
        val stateDto = StateDto(
            key,
            "a".toByteArray(),
            1,
            jsonb,
            Instant.now(),
        )

        emf.createEntityManager().transaction { em ->
            stateManagerRepository.put(em, listOf(stateDto))
        }
    }

    @Test
    fun `can persist and load the same state using state manager`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val key = UUID.randomUUID().toString()
        val jsonb = """{"metadatakey_$key": "metadatavalue_$key"}"""
        val modifiedTime = Instant.now()
        val stateDto = StateDto(
            key,
            "a".toByteArray(),
            1,
            jsonb,
            modifiedTime,
        )

        emf.createEntityManager().transaction { em ->
            stateManagerRepository.put(em, listOf(stateDto))
        }

        val retrievedState = emf.createEntityManager().transaction { em ->
            stateManagerRepository.get(em, listOf(key))
        }

        assertThat(retrievedState).hasSize(1)
        assertThat(retrievedState[0].key).isEqualTo(key)
        assertThat(retrievedState[0].metadata).isEqualTo(jsonb)
    }
}