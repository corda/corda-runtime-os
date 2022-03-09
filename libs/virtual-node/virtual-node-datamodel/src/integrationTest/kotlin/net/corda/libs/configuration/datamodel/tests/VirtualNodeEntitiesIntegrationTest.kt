package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

class VirtualNodeEntitiesIntegrationTest {
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
            val dbConfig = DbUtils.getEntityManagerConfiguration("virtual_node_db")

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
                VirtualNodeEntities.classes.toList() + CpiEntities.classes.toList(),
                dbConfig
            )
        }
    }

    @Test
    fun `can persist and read back Holding Identity entity`() {
        val holdingIdentity = HoldingIdentityEntity("0123456789AB", "a=b", "OU=LLC, O=Bob, L=Dublin, C=IE",
            "${random.nextInt()}", null, null, null, null, null)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(holdingIdentity)
        }

        assertEquals(
            holdingIdentity,
            entityManagerFactory.createEntityManager().find(HoldingIdentityEntity::class.java, holdingIdentity.holdingIdentityId)
        )
    }

    @Test
    fun `can persist and read back Virtual Node entity`() {
        val cpiMetadata = CpiMetadataEntity("Test CPI","1.0","CPI summary hash",
                    "123456789012","123456789012","file","1234567890",
                    "group policy","group ID", "request ID")
        val virtualNode = VirtualNodeEntity("0123456789AB", "Test CPI", "1.0",
            "CPI summary hash")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.persist(virtualNode)
        }

        assertEquals(
            virtualNode,
            entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java,
                VirtualNodeEntityKey(virtualNode.holdingIdentityId, virtualNode.cpiName, virtualNode.cpiVersion, virtualNode.cpiSignerSummaryHash))
        )
    }
}