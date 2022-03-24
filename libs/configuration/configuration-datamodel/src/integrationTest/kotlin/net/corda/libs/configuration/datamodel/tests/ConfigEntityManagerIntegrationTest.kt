package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.DbConnectionAudit
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.datamodel.findDbConnectionAuditByNameAndPrivilege
import net.corda.libs.configuration.datamodel.findDbConnectionByNameAndPrivilege
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

class ConfigEntityManagerIntegrationTest {
    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
        private lateinit var entityManagerFactory: EntityManagerFactory
        private val random = Random(0)

        /**
         * Creates an in-memory database, applies the relevant migration scripts, and initialises
         * [entityManagerFactory].
         */
        @Suppress("Unused")
        @BeforeAll
        @JvmStatic
        private fun prepareDatabase() {
            val dbConfig = DbUtils.getEntityManagerConfiguration("configuration_db")

            val dbChange = ClassloaderChangeLog(
                linkedSetOf(
                    ChangeLogResourceFiles(
                        DbSchema::class.java.packageName,
                        listOf(MIGRATION_FILE_LOCATION),
                        DbSchema::class.java.classLoader
                    )
                )
            )
            dbConfig.dataSource.connection.use { connection ->
                LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
            }
            entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
                "test_unit",
                ConfigurationEntities.classes.toList(),
                dbConfig
            )
        }
    }

    @Test
    fun `can persist and read back config entities`() {
        val config = ConfigEntity("${random.nextInt()}", "a=b", 999,
            // truncating to millis as on windows builds the micros are lost after fetching the data from Postgres
            Instant.now().truncatedTo( ChronoUnit.MILLIS ), "actor")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(config)
        }

        assertEquals(
            config,
            entityManagerFactory.createEntityManager().find(ConfigEntity::class.java, config.section)
        )
    }

    @Test
    fun `can persist and read back config audit entities`() {
        val config = ConfigEntity("${random.nextInt()}", "a=b", 999,
            // truncating to millis as on windows builds the micros are lost after fetching the data from Postgres
            Instant.now().truncatedTo( ChronoUnit.MILLIS ), "joel")
        val configAudit = ConfigAuditEntity(config)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(configAudit)
        }

        assertEquals(
            configAudit,
            entityManagerFactory.createEntityManager().find(ConfigAuditEntity::class.java, configAudit.changeNumber)
        )
    }

    @Test
    fun `can persiste and read back db connection configs`() {
        val dbConnection = DbConnectionConfig(
            UUID.randomUUID(),
            "batman",
            DbPrivilege.DDL,
            // truncating to millis as on windows builds the micros are lost after fetching the data from Postgres
            Instant.now().truncatedTo( ChronoUnit.MILLIS ),
            "the joker",
            "The Night Is Darkest Right Before The Dawn.",
            """
hello=world
            """.trimIndent()
        )
        val dbConnectionAudit = DbConnectionAudit(dbConnection)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(dbConnection)
        }

        assertEquals(
            dbConnection,
            entityManagerFactory.createEntityManager().findDbConnectionByNameAndPrivilege("batman", DbPrivilege.DDL)
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(dbConnectionAudit)
        }

        assertEquals(
            dbConnectionAudit,
            entityManagerFactory.createEntityManager().findDbConnectionAuditByNameAndPrivilege("batman", DbPrivilege.DDL)
        )
    }
}