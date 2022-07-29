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
import net.corda.libs.virtualnode.datamodel.findAllVirtualNodes
import net.corda.libs.virtualnode.datamodel.findVirtualNode
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.persistence.EntityManagerFactory
import kotlin.random.Random
import kotlin.streams.toList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualNodeEntitiesIntegrationTest {
    private val dbConfig = DbUtils.getEntityManagerConfiguration("virtual_node_db")
    private val entityManagerFactory: EntityManagerFactory

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
        private val random = Random(0)
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
            VirtualNodeEntities.classes.toList() + CpiEntities.classes.toList(),
            dbConfig
        )
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        dbConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun `can persist and read back Holding Identity entity`() {
        val holdingIdentityShortHash = Generator.randomHoldingIdentityShortHash()
        val holdingIdentity = HoldingIdentityEntity(
            holdingIdentityShortHash, "a=b", "OU=LLC, O=Bob, L=Dublin, C=IE",
            "${random.nextInt()}", null, null, null, null, null
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(holdingIdentity)
        }

        assertEquals(
            holdingIdentity,
            entityManagerFactory.createEntityManager()
                .find(HoldingIdentityEntity::class.java, holdingIdentity.holdingIdentityShortHash)
        )
    }

    @Test
    fun `can persist and read back Virtual Node entity and holding identity in one transaction`() {
        val name = "Test CPI"
        val version = "1.0-${Generator.epochMillis()}"
        val hash = "CPI summary hash"

        val cpiMetadata = newCpiMetadataEntity(name, version, hash)
        val holdingIdentityShortHash = Generator.randomHoldingIdentityShortHash()
        val entity = newHoldingIdentityEntity(holdingIdentityShortHash)

        val holdingIdentityEntity = entityManagerFactory.createEntityManager()
            .transaction { em -> em.getReference(HoldingIdentityEntity::class.java, holdingIdentityShortHash) }

        val virtualNode = VirtualNodeEntity(entity, name, version, hash, "")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.persist(virtualNode)
        }

        val key = VirtualNodeEntityKey(holdingIdentityEntity, name, version, hash)

        assertEquals(virtualNode, entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java, key))
    }

    @Test
    fun `can persist and read back Virtual Node entity with holding identity in two transactions`() {
        val name = "Test CPI"
        val version = "1.0-${Generator.epochMillis()}"
        val hash = "CPI summary hash"

        val cpiMetadata = newCpiMetadataEntity(name, version, hash)
        val holdingIdentityShortHash = Generator.randomHoldingIdentityShortHash()
        val holdingIdentityEntity = newHoldingIdentityEntity(holdingIdentityShortHash)

        // Persist holding identity...
        entityManagerFactory.createEntityManager().transaction { em -> em.persist(holdingIdentityEntity) }

        // Now persist the virtual node but use the merge operation because we've
        // already persisted the holding identity (i.e. REST end-point - "create holding identity")
        val virtualNode = VirtualNodeEntity(holdingIdentityEntity, name, version, hash, "")
        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.merge(virtualNode)
        }

        // Use a reference to *find* only - we do NOT need the other fields in the HoldingIdentityEntity
        // (and in fact, neither does hibernate - it only cares about the primary keys).
        val holdingIdentityReference = entityManagerFactory.createEntityManager()
            .transaction { em -> em.getReference(HoldingIdentityEntity::class.java, holdingIdentityShortHash) }
        val key = VirtualNodeEntityKey(holdingIdentityReference, name, version, hash)

        assertEquals(virtualNode, entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java, key))
    }

    @Test
    fun `virtual node entity query test`() {
        // "set up"
        val numberOfVNodes = 5
        for (i in 1..numberOfVNodes) {
            newVNode("Test CPI $i", "1.0-${Generator.epochMillis()}", "hash$i")
        }

        // Now check the query - and also we should look at the console output for this
        val virtualNodes = entityManagerFactory.createEntityManager().findAllVirtualNodes().toList()

        // Might be more than 3 due to junk from other tests.
        println("Found ${virtualNodes.size} virtual nodes in db table")
        assertTrue(virtualNodes.size >= numberOfVNodes)

        // And check that every vnode name is in the list we get back
        for (i in 1..numberOfVNodes) {
            assertTrue(virtualNodes.map { it.cpiName }.contains("Test CPI $i"))
            // This assert also checks that the x500 field, which *isn't* a primary key field,
            // is fetched immediately during the transaction.
            // If this fails, the query has been changed and we're fetching non-pk fields lazily
            // which is NOT what we want.
            assertTrue(virtualNodes.map { it.holdingIdentity.x500Name }.size >= numberOfVNodes)
        }
    }

    @Test
    fun `lookup virtual node entity query test`() {
        // "set up"
        val numberOfVNodes = 5
        val vnodes = (1..numberOfVNodes).map { i ->
            newVNode("Test CPI $i", "1.0-${Generator.epochMillis()}", "hash$i")
        }

        // Now check the query - and also we should look at the console output for this
        val virtualNode = entityManagerFactory.createEntityManager().use {
            it.findVirtualNode(vnodes.last().holdingIdentity.holdingIdentityShortHash)
        }!!

        // Validate relation for holdingIdentity has been resolved
        assertNotNull(virtualNode.holdingIdentity.x500Name)
        assertEquals(virtualNode.holdingIdentity.x500Name, vnodes.last().holdingIdentity.x500Name)
    }

    private fun newVNode(name: String, version: String, hash: String): VirtualNodeEntity {
        val cpiMetadata = newCpiMetadataEntity(name, version, hash)
        val holdingIdentity = newHoldingIdentityEntity(Generator.randomHoldingIdentityShortHash())
        val virtualNode = VirtualNodeEntity(holdingIdentity, name, version, hash, "")

        entityManagerFactory.createEntityManager().transaction { em -> em.persist(holdingIdentity) }
        entityManagerFactory.createEntityManager().transaction { em -> em.persist(cpiMetadata) }
        entityManagerFactory.createEntityManager().transaction { em -> return em.merge(virtualNode) }
    }

    private fun newHoldingIdentityEntity(holdingIdentityShortHash: String) = HoldingIdentityEntity(
        holdingIdentityShortHash = holdingIdentityShortHash,
        holdingIdentityFullHash = "1234",
        x500Name = "dummy",
        mgmGroupId = "dummy",
        vaultDDLConnectionId = null,
        vaultDMLConnectionId = null,
        cryptoDDLConnectionId = null,
        cryptoDMLConnectionId = null,
        hsmConnectionId = null
    )

    private fun newCpiMetadataEntity(
        name: String,
        version: String,
        hash: String
    ) = CpiMetadataEntity(
        name = name,
        version = version,
        signerSummaryHash = hash,
        fileName = "file",
        fileChecksum = Generator.randomHoldingIdentityShortHash(),
        groupPolicy = "group policy",
        groupId = "group ID",
        fileUploadRequestId = "request ID",
        cpks = emptySet()
    )
}
