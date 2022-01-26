package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.InMemoryEntityManagerConfiguration
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

class EntityManagerFactoryFactoryIntegrationTest {
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
            val dbConfig = InMemoryEntityManagerConfiguration("test_db")

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
                listOf(ConfigEntity::class.java, ConfigAuditEntity::class.java),
                dbConfig
            )
        }
    }

    @Test
    fun `can persist and read back config entities`() {
        val config = ConfigEntity("${random.nextInt()}", "a=b", 999, Instant.now(), "actor")

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
        val config = ConfigEntity("${random.nextInt()}", "a=b", 999, Instant.now(), "joel")
        val configAudit = ConfigAuditEntity(config)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(configAudit)
        }

        assertEquals(
            configAudit,
            entityManagerFactory.createEntityManager().find(ConfigAuditEntity::class.java, configAudit.changeNumber)
        )
    }
}