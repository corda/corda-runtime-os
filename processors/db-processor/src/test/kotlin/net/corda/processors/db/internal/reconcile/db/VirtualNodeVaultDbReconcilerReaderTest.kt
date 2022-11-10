package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.processors.db.internal.reconcile.db.query.VaultReconciliationQuery
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class VirtualNodeVaultDbReconcilerReaderTest {

    private fun buildVirtualNodeInfo(uuid: UUID) = VirtualNodeInfo(
        HoldingIdentity(
            x500Name = MemberX500Name.parse("O=Corda, L=London, C=GB, OU=${uuid}"),
            groupId = uuid.toString()
        ),
        CpiIdentifier(
            name = "my-cpi-${uuid}",
            version = "1.3",
            signerSummaryHash = null
        ),
        vaultDmlConnectionId = uuid,
        cryptoDmlConnectionId = uuid,
        uniquenessDmlConnectionId = uuid,
        timestamp = Instant.ofEpochSecond(1)
    )

    private val virtualNode1 = buildVirtualNodeInfo(UUID(0, 1))
    private val virtualNode2 = buildVirtualNodeInfo(UUID(1, 2))
    private val virtualNodeInfos = listOf(virtualNode1, virtualNode2)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getAll() } doReturn virtualNodeInfos
    }

    private val jpaEntitiesSet: JpaEntitiesSet = mock()
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn jpaEntitiesSet
    }

    private val vnode1Transaction: EntityTransaction = mock()
    private val vnode1Em: EntityManager = mock {
        on { transaction } doReturn vnode1Transaction
    }
    private val vnode1Emf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn vnode1Em
    }

    private val vnode2Transaction: EntityTransaction = mock()
    private val vnode2Em: EntityManager = mock {
        on { transaction } doReturn vnode2Transaction
    }
    private val vnode2Emf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn vnode2Em
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(eq(virtualNode1.vaultDmlConnectionId), eq(jpaEntitiesSet)) } doReturn vnode1Emf
        on { createEntityManagerFactory(eq(virtualNode2.vaultDmlConnectionId), eq(jpaEntitiesSet)) } doReturn vnode2Emf
    }

    private val vnode1VersionedRecord: VersionedRecord<String, Int> = mock()
    private val vnode2VersionedRecord1: VersionedRecord<String, Int> = mock()
    private val vnode2VersionedRecord2: VersionedRecord<String, Int> = mock()
    private val vaultReconciliationQuery: VaultReconciliationQuery<String, Int> = mock {
        on { invoke(eq(virtualNode1), eq(vnode1Em)) } doReturn listOf(vnode1VersionedRecord)
        on { invoke(eq(virtualNode2), eq(vnode2Em)) } doReturn listOf(vnode2VersionedRecord1, vnode2VersionedRecord2)
    }
    private val keyClass: Class<String> = String::class.java
    private val valueClass: Class<Int> = Int::class.java

    private val mockExceptionHandler: (Exception) -> Unit = mock()

    data class VNodeMocks(
        val virtualNodeInfo: VirtualNodeInfo,
        val emf: EntityManagerFactory,
        val em: EntityManager,
        val transaction: EntityTransaction
    )

    val vnode1Mocks = VNodeMocks(virtualNode1, vnode1Emf, vnode1Em, vnode1Transaction)
    val vnode2Mocks = VNodeMocks(virtualNode2, vnode2Emf, vnode2Em, vnode2Transaction)

    private val virtualNodeVaultDbReconcilerReader = VirtualNodeVaultDbReconcilerReader(
        virtualNodeInfoReadService,
        dbConnectionManager,
        jpaEntitiesRegistry,
        vaultReconciliationQuery,
        keyClass,
        valueClass
    ).also { it.registerExceptionHandler(mockExceptionHandler) }

    @Test
    fun `Service name is set as expected`() {
        assertThat(virtualNodeVaultDbReconcilerReader.name)
            .isEqualTo("VirtualNodeVaultDbReconcilerReader<String, int>")
    }

    @Test
    fun `Service depends on expected services`() {
        assertThat(virtualNodeVaultDbReconcilerReader.dependencies)
            .isEqualTo(
                setOf(
                    LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
                )
            )
    }

    @Test
    fun `getAllVersionedRecords runs successfully`() {
        assertDoesNotThrow {
            virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()
        }
    }

    @Test
    fun `All vnode versioned records are returned as a stream`() {
        val resultStream = virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()

        assertThat(resultStream)
            .containsExactlyInAnyOrder(vnode1VersionedRecord, vnode2VersionedRecord1, vnode2VersionedRecord2)
    }

    @Test
    fun `Expected services are called`() {
        virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()

        verify(virtualNodeInfoReadService).getAll()

        listOf(vnode1Mocks, vnode2Mocks).forEach {
            verify(dbConnectionManager).createEntityManagerFactory(
                eq(it.virtualNodeInfo.vaultDmlConnectionId), eq(jpaEntitiesSet)
            )
            verify(it.emf).createEntityManager()
            verify(it.emf).close()
            verify(it.em).transaction
            verify(it.em).close()
            verify(it.transaction).begin()
            verify(it.transaction).commit()
            verify(vaultReconciliationQuery).invoke(eq(it.virtualNodeInfo), eq(it.em))
        }

        verify(mockExceptionHandler, never()).invoke(any())
    }

    @Test
    fun `empty stream is returned if no virtual nodes have been created`() {
        whenever(virtualNodeInfoReadService.getAll()).doReturn(emptyList())
        val resultStream = assertDoesNotThrow {
            virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()
        }
        assertThat(resultStream).isEmpty()
    }

    @Test
    fun `exception retrieving the virtual nodes is caught and error event is posted to the coordinator`() {
        whenever(virtualNodeInfoReadService.getAll()).doThrow(RuntimeException::class)
        val resultStream = assertDoesNotThrow {
            virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()
        }
        assertThat(resultStream).isNull()
        verify(virtualNodeInfoReadService).getAll()
        verify(dbConnectionManager, never()).createEntityManagerFactory(any(), any())
        verify(mockExceptionHandler).invoke(any())
    }

    @Test
    fun `exception creating the entity manager factory is caught and error event is posted to the coordinator`() {
        whenever(dbConnectionManager.createEntityManagerFactory(any(), any())).doThrow(RuntimeException::class)
        val resultStream = assertDoesNotThrow {
            virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()
        }
        assertThat(resultStream).isNull()
        verify(dbConnectionManager).createEntityManagerFactory(any(), any())
        verify(vnode1Emf, never()).createEntityManager()
        verify(vnode2Emf, never()).createEntityManager()
        verify(mockExceptionHandler).invoke(any())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `exception creating the entity manager is caught, entity manager factory is closed, and error event is posted to the coordinator`() {
        whenever(vnode1Emf.createEntityManager()).doThrow(RuntimeException::class)
        val resultStream = assertDoesNotThrow {
            virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()
        }
        assertThat(resultStream).isNull()
        verify(vnode1Emf).createEntityManager()
        verify(vnode1Emf).close()
        verify(vnode1Em, never()).transaction
        verify(mockExceptionHandler).invoke(any())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `exception running the query is caught, entity manager and entity manager factory are closed, and error event is posted to the coordinator`() {
        whenever(vaultReconciliationQuery.invoke(eq(virtualNode1), eq(vnode1Em))).doThrow(RuntimeException::class)
        val resultStream = assertDoesNotThrow {
            virtualNodeVaultDbReconcilerReader.getAllVersionedRecords()
        }
        assertThat(resultStream).isNull()
        verify(vaultReconciliationQuery).invoke(eq(virtualNode1), eq(vnode1Em))
        verify(vnode1Emf).close()
        verify(vnode1Em).close()
        verify(mockExceptionHandler).invoke(any())
    }
}