package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.virtualnode.datamodel.*
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.persistence.EntityManagerFactory

class VirtualNodeVaultEntitiesIntegrationTest {
    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/vnode-vault/db.changelog-master.xml"
        private lateinit var entityManagerFactory: EntityManagerFactory

        /**
         * Creates an in-memory database, applies the relevant migration scripts, and initialises
         * [entityManagerFactory].
         */
        @Suppress("Unused")
        @BeforeAll
        @JvmStatic
        private fun prepareDatabase() {
            val dbConfig = DbUtils.getEntityManagerConfiguration("vnode_vault_db")

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
                VaultEntities.classes.toList(),
                dbConfig
            )
        }
    }

    @Test
    fun `can persist and read back Vault entity`() {
        val vault = VaultEntity("123456789012")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(vault)
        }

        assertEquals(
            vault,
            entityManagerFactory.createEntityManager().find(VaultEntity::class.java, vault.holdingIdentityId)
        )
    }
}