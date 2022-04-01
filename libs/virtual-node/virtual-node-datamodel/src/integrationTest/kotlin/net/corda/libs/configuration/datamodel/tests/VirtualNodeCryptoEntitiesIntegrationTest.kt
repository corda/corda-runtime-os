package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.virtualnode.datamodel.*
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualNodeCryptoEntitiesIntegrationTest {
    private val dbConfig = DbUtils.getEntityManagerConfiguration("vnode_crypto_db")
    private val entityManagerFactory: EntityManagerFactory

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/vnode-crypto/db.changelog-master.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * [entityManagerFactory].
     */
    init {
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
            CryptoEntities.classes.toList(),
            dbConfig
        )
    }

    @Suppress("Unused")
    @AfterAll
    private fun cleanup() {
        dbConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun `can persist and read back Key entity`() {
        val key = KeyEntity("0123456789AB")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(key)
        }

        assertEquals(
            key,
            entityManagerFactory.createEntityManager().find(KeyEntity::class.java, key.holdingIdentityId)
        )
    }

    @Test
    fun `can persist and read back Certificate entity`() {
        val certificate = CertificateEntity("0123456789AB")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(certificate)
        }

        assertEquals(
            certificate,
            entityManagerFactory.createEntityManager()
                .find(CertificateEntity::class.java, certificate.holdingIdentityId)
        )
    }
}