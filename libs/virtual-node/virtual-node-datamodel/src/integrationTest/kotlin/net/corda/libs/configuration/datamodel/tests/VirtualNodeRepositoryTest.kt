package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.test.util.TestRandom
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.streams.toList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualNodeRepositoryTest {
    private val dbConfig = DbUtils.getEntityManagerConfiguration(this::class.java.simpleName)
    private val entityManagerFactory: EntityManagerFactory

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
    }

    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
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
            this.javaClass.simpleName,
            VirtualNodeEntities.classes.toList() + CpiEntities.classes.toList() + ConfigurationEntities.classes.toList(),
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
    fun findAll() {
        // "set up"
        val numberOfVNodes = 5
        for (i in 1..numberOfVNodes) {
            VNodeTestUtils.newVNode(
                entityManagerFactory,
                "Test CPI $i",
                "1.0-${Instant.now().toEpochMilli()}",
                TestRandom.secureHash().toString())
        }

        // Now check the query - and also we should look at the console output for this
        entityManagerFactory.createEntityManager().use { em ->
            val virtualNodes = VirtualNodeRepositoryImpl().findAll(em).toList()

            // Might be more than 3 due to junk from other tests.
            println("Found ${virtualNodes.size} virtual nodes in db table")
            Assertions.assertTrue(virtualNodes.size >= numberOfVNodes)

            // And check that every vnode name is in the list we get back
            for (i in 1..numberOfVNodes) {
                Assertions.assertTrue(virtualNodes.map { it.cpiIdentifier.name }.contains("Test CPI $i"))
                // This assert also checks that the x500 field, which *isn't* a primary key field,
                // is fetched immediately during the transaction.
                // If this fails, the query has been changed and we're fetching non-pk fields lazily
                // which is NOT what we want.
                Assertions.assertTrue(virtualNodes.map { it.holdingIdentity.x500Name }.size >= numberOfVNodes)
            }
        }
    }

    @Test
    fun find() {
        // "set up"
        val numberOfVNodes = 5
        val vnodes = (1..numberOfVNodes).map {
            VNodeTestUtils.newVNode(
                entityManagerFactory,
                "Test CPI ${UUID.randomUUID()}",
                "1.0-${Instant.now().toEpochMilli()}",
                TestRandom.secureHash().toString())
        }

        // Now check the query - and also we should look at the console output for this
        val virtualNode = entityManagerFactory.createEntityManager().use {
            VirtualNodeRepositoryImpl().find(it, ShortHash.of(vnodes.last().holdingIdentity.holdingIdentityShortHash))
        }!!

        // Validate relation for holdingIdentity has been resolved
        assertNotNull(virtualNode.holdingIdentity.x500Name)
        assertEquals(virtualNode.holdingIdentity.x500Name.toString(), vnodes.last().holdingIdentity.x500Name)
    }

    @Test
    fun `find returns null if no vnode found`() {
        // "set up"
        val numberOfVNodes = 5
        (1..numberOfVNodes).forEach { i ->
            VNodeTestUtils.newVNode(
                entityManagerFactory,
                "Test CPI $i ${UUID.randomUUID()}",
                "1.0-${Instant.now().toEpochMilli()}",
                TestRandom.secureHash().toString())
        }

        val virtualNode = entityManagerFactory.createEntityManager().use {
            VirtualNodeRepositoryImpl().find(it, ShortHash.of(TestRandom.holdingIdentityShortHash()))
        }

        assertEquals(virtualNode, null)
    }

    @Test
    fun put() {
        val hash = TestRandom.secureHash()
        val vnode = VNodeTestUtils.newVNode(entityManagerFactory, "Testing ${UUID.randomUUID()}", "1.0", hash.toString())

        val hi = vnode.holdingIdentity.toHoldingIdentity()
        val cpiId = CpiIdentifier(vnode.cpiName, vnode.cpiVersion, hash)

        entityManagerFactory.createEntityManager().transaction {
            VirtualNodeRepositoryImpl().put(
                it,
                hi,
                cpiId,
                vnode.vaultDDLConnectionId,
                vnode.vaultDMLConnectionId!!,
                vnode.cryptoDDLConnectionId,
                vnode.cryptoDMLConnectionId!!,
                vnode.uniquenessDDLConnectionId,
                vnode.uniquenessDMLConnectionId,
            )
        }

        val putEntity = entityManagerFactory.createEntityManager().use {
            VirtualNodeRepositoryImpl().find(it, hi.shortHash)
        }

        assertThat(putEntity).isNotNull
        assertThat(putEntity!!.holdingIdentity).isEqualTo(hi)
        assertThat(putEntity.cpiIdentifier).isEqualTo(cpiId)
    }

    @Test
    fun `put throws when Holding Identity does not exist`() {
        val hi = HoldingIdentity(
            MemberX500Name.Companion.parse("C=GB,L=London,O=Test"),
            "group"
        )
        val cpiId = CpiIdentifier("cpi ${UUID.randomUUID()}", "1.0", TestRandom.secureHash())

        assertThrows<CordaRuntimeException> {
            entityManagerFactory.createEntityManager().transaction {
                VirtualNodeRepositoryImpl().put(
                    it,
                    hi,
                    cpiId,
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID())
            }
        }
    }

    @Test
    fun updateVirtualNodeState() {
        val hash = TestRandom.secureHash()
        val vnode = VNodeTestUtils
            .newVNode(entityManagerFactory, "Testing ${UUID.randomUUID()}", "1.0", hash.toString())

        entityManagerFactory.createEntityManager().use {
            VirtualNodeRepositoryImpl().updateVirtualNodeState(
                it, vnode.holdingIdentity.holdingIdentityShortHash, "maintenance")
        }

        val changedEntity = entityManagerFactory.createEntityManager().use {
            VirtualNodeRepositoryImpl().find(it, ShortHash.of(vnode.holdingIdentity.holdingIdentityShortHash))
        }

        assertThat(changedEntity).isNotNull
        assertThat(changedEntity!!.flowP2pOperationalStatus).isEqualTo(OperationalStatus.INACTIVE)
        assertThat(changedEntity!!.flowStartOperationalStatus).isEqualTo(OperationalStatus.INACTIVE)
        assertThat(changedEntity!!.flowOperationalStatus).isEqualTo(OperationalStatus.INACTIVE)
        assertThat(changedEntity!!.vaultDbOperationalStatus).isEqualTo(OperationalStatus.INACTIVE)
    }
}