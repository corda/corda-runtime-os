package net.corda.libs.statemanager.impl.tests

import java.time.Instant
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
import org.junit.jupiter.api.AfterAll
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

        val jsonb = """
            {"metadatakey": "metadatavalue"} 
            """.trimIndent()
        val stateDtoA = StateDto(
            "a_key",
            "a".toByteArray(),
            1,
            jsonb,
            Instant.now(),
        )

        emf.createEntityManager().transaction { em ->
            stateManagerRepository.put(em, listOf(stateDtoA))
        }
    }
}