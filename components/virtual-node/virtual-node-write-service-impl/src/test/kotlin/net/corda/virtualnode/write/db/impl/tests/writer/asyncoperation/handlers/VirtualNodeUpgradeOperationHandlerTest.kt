package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.MgmGroupMismatchException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeUpgradeOperationHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.PersistenceException
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogKey
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.VirtualNodeStateException

class VirtualNodeUpgradeOperationHandlerTest {
    private val oldVirtualNodeEntityRepository = mock<VirtualNodeEntityRepository>()
    private val virtualNodeInfoPublisher = mock<Publisher>()
    private val virtualNodeRepository = mock<VirtualNodeRepository>()
    private val entityTransaction = mock<EntityTransaction>()
    private val em = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val migrationUtility = mock<MigrationUtility>() {
        whenever(it.isVaultSchemaAndTargetCpiInSync(any(), any())).thenReturn(false)
    }
    private val vnodeId = "123456789011"

    private val mockChangelog1 = mock<CpkDbChangeLogEntity> { changelog ->
        whenever(changelog.id).thenReturn(CpkDbChangeLogKey("cpk1", "dog.xml"))
    }
    private val mockChangelog2 = mock<CpkDbChangeLogEntity> { changelog ->
        whenever(changelog.id).thenReturn(CpkDbChangeLogKey("cpk1", "cat.xml"))
    }
    private val cpkDbChangelogs = listOf(mockChangelog1, mockChangelog2)
    private val getCurrentChangelogsForCpi = mock<(EntityManager, String, String, String) -> List<CpkDbChangeLogEntity>> {
        whenever(it(any(), any(), any(), any())).thenReturn(cpkDbChangelogs)
    }

    private val handler = VirtualNodeUpgradeOperationHandler(
        entityManagerFactory,
        oldVirtualNodeEntityRepository,
        virtualNodeInfoPublisher,
        migrationUtility,
        getCurrentChangelogsForCpi,
        virtualNodeRepository
    )

    private val sshBytes = ByteArray(16)
    private val ssh = SecureHash("SHA-256", sshBytes)
    private val sshString = ssh.toString()
    private val cpiName = "someCpi"
    private val currentCpiId = mock<CpiIdentifier>() {
        whenever(it.name).thenReturn(cpiName)
        whenever(it.version).thenReturn("v1")
        whenever(it.signerSummaryHash).thenReturn(ssh)
    }
    private val vNode = mock<VirtualNodeInfo>() {
        whenever(it.cpiIdentifier).thenReturn(currentCpiId)
        whenever(it.flowOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        whenever(it.flowStartOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        whenever(it.flowP2pOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        whenever(it.vaultDbOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
    }
    private val groupName = "someGroup1"
    private val targetCpiChecksum = "targetCpi"
    private val currentCpiMetadata = mock<CpiMetadataLite>() {
        whenever(it.mgmGroupId).thenReturn(groupName)
    }
    private val targetCpiId = CpiIdentifier(cpiName, "v2", ssh)
    private val targetCpiMetadata = mock<CpiMetadataLite>() {
        whenever(it.mgmGroupId).thenReturn(groupName)
        whenever(it.id).thenReturn(targetCpiId)
    }

    private val x500Name = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")
    private val mockHoldingIdentity = HoldingIdentity(x500Name, groupName)
    val vaultDmlConnectionId = UUID.randomUUID()
    private val vnodeInfoWithoutVaultDdl = VirtualNodeInfo(
        mockHoldingIdentity,
        targetCpiId,
        null,
        vaultDmlConnectionId,
        null,
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        timestamp = Instant.now()
    )
    private val vaultDdlConnectionId = UUID.randomUUID()
    // todo cs - when we add operationinProgress to avro type, add it to there virtualNodeInfo objects
    private val inProgressOpVnodeInfo = VirtualNodeInfo(
        mockHoldingIdentity,
        targetCpiId,
        vaultDdlConnectionId,
        vaultDmlConnectionId,
        null,
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        timestamp = Instant.now()
    )
    private val migrationsCompleteVnodeInfo = VirtualNodeInfo(
        mockHoldingIdentity,
        targetCpiId,
        vaultDdlConnectionId,
        vaultDmlConnectionId,
        null,
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        timestamp = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        whenever(em.transaction).thenReturn(entityTransaction)
        whenever(entityManagerFactory.createEntityManager()).thenReturn(em)
    }

    @Test
    fun `upgrade handler validates virtual node identifier is not null`() {
        assertThrows<IllegalArgumentException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(
                    null, "aaaa", null
                )
            )
        }
    }

    @Test
    fun `upgrade handler validates target cpiFileChecksum is not null`() {
        assertThrows<IllegalArgumentException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(
                    vnodeId, null, null
                )
            )
        }
    }

    @Test
    fun `upgrade handler can't find virtual node throws`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId)))
            .thenReturn(null)

        assertThrows<VirtualNodeNotFoundException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
            )
        }
    }

    @Test
    fun `upgrade handler validates vault_db_operational_status is INACTIVE`() {
        val activeVnode = mock<VirtualNodeInfo> {
            whenever(it.cpiIdentifier).thenReturn(currentCpiId)
            whenever(it.flowOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
            whenever(it.flowStartOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
            whenever(it.flowP2pOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
            whenever(it.vaultDbOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
        }
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(activeVnode)

        assertThrows<VirtualNodeStateException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
            )
        }
    }

    @Test
    fun `upgrade handler can't find target CPI throws`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(null)

        assertThrows<CpiNotFoundException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
            )
        }
    }

    @Test
    fun `upgrade handler can't find current CPI associated with target CPI throws`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(null)

        assertThrows<IllegalArgumentException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
            )
        }
    }

    @Test
    fun `upgrade handler validates target CPI and current CPI are in the same group`() {
        val cpiInDifferentGroup = mock<CpiMetadataLite> { whenever(it.mgmGroupId).thenReturn("group-b") }
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(cpiInDifferentGroup)

        assertThrows<MgmGroupMismatchException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
            )
        }
    }

    @Test
    fun `upgrade handler fails to upgrade, rolls back transaction`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(virtualNodeRepository.upgradeVirtualNodeCpi(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("err"))

        whenever(entityTransaction.rollbackOnly).thenReturn(true)

        assertThrows<IllegalArgumentException> {
            handler.handle(
                Instant.now(),
                "req1",
                VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
            )
        }

        verify(entityTransaction, times(1)).rollback()
    }

    @Test
    fun `upgrade handler successfully persists and publishes vnode info`() {
        val requestTimestamp = Instant.now()
        val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq("req1"), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(vnodeInfoWithoutVaultDdl)

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, "req1", request)

        verify(virtualNodeInfoPublisher, times(1)).publish(vnodeInfoCapture.capture())

        val publishedRecordList = vnodeInfoCapture.firstValue
        assertThat(publishedRecordList).isNotNull
        assertThat(publishedRecordList).hasSize(1)

        val publishedRecord = publishedRecordList[0]
        assertThat(publishedRecord.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        assertThat(publishedRecord.key.groupId).isEqualTo(groupName)
        assertThat(publishedRecord.key.x500Name).isEqualTo(x500Name.toString())

        assertThat(publishedRecord.value).isNotNull
        assertThat(publishedRecord.value!!.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(publishedRecord.value!!.cpiIdentifier.version).isEqualTo("v2")
    }

    @Test
    fun `upgrade handler throws when running migrations`() {
        val requestTimestamp = Instant.now()
        val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq("req1"), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(migrationUtility.runVaultMigrations(any(), any(), any())).thenThrow(PersistenceException("Some liquibase exception"))

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        assertThrows<PersistenceException> {
            handler.handle(requestTimestamp, "req1", request)
        }

        verify(virtualNodeInfoPublisher, times(1)).publish(vnodeInfoCapture.capture())

        val publishedRecordList = vnodeInfoCapture.firstValue
        assertThat(publishedRecordList).isNotNull
        assertThat(publishedRecordList).hasSize(1)

        val publishedRecord = publishedRecordList[0]
        assertThat(publishedRecord.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        assertThat(publishedRecord.key.groupId).isEqualTo(groupName)
        assertThat(publishedRecord.key.x500Name).isEqualTo(x500Name.toString())

        assertThat(publishedRecord.value).isNotNull
        assertThat(publishedRecord.value!!.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(publishedRecord.value!!.cpiIdentifier.version).isEqualTo("v2")
    }

    @Test
    fun `upgrade handler successfully persists, runs migrations with vault ddl, publishes vnode info and completes operation`() {
        val requestTimestamp = Instant.now()
        val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(virtualNodeRepository.upgradeVirtualNodeCpi(
            eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq("req1"), eq(requestTimestamp), eq(request.toString()))
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(virtualNodeRepository.completeOperation(em, request.virtualNodeShortHash)).thenReturn(migrationsCompleteVnodeInfo)

        val inProgressVNodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()
        // todo cs - when we add virtual node info, separate these publishes (one should be in progress operation, the other complete)
//        val upgradeCompleteVNodeInfoCapture =
//            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, "req1", request)

        verify(virtualNodeInfoPublisher, times(2)).publish(inProgressVNodeInfoCapture.capture())
        verify(migrationUtility).runVaultMigrations(
            eq(ShortHash.of(request.virtualNodeShortHash)),
            eq(cpkDbChangelogs),
            eq(vaultDdlConnectionId)
        )
        // todo cs - when we add virtual node info, separate these publishes (one should be in progress operation, the other complete)
//        verify(virtualNodeInfoPublisher, times(1)).publish(upgradeCompleteVNodeInfoCapture.capture())

        val publishedRecordList = inProgressVNodeInfoCapture.firstValue
        assertThat(publishedRecordList).isNotNull
        assertThat(publishedRecordList).hasSize(1)

        val publishedRecord = publishedRecordList[0]
        assertThat(publishedRecord.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        assertThat(publishedRecord.key.groupId).isEqualTo(groupName)
        assertThat(publishedRecord.key.x500Name).isEqualTo(x500Name.toString())

        assertThat(publishedRecord.value).isNotNull
        assertThat(publishedRecord.value!!.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(publishedRecord.value!!.cpiIdentifier.version).isEqualTo("v2")

        // todo cs - after we add operationInProgress to virtualNodeInfo, assertions here that after completion, it is null
    }

    @Test
    fun `upgrade handler successfully persists, no migrations required`() {
        val requestTimestamp = Instant.now()
        val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)
        val migrationUtility = mock<MigrationUtility>() {
            whenever(it.isVaultSchemaAndTargetCpiInSync(any(), any())).thenReturn(false)
        }

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(virtualNodeRepository.upgradeVirtualNodeCpi(
            eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq("req1"), eq(requestTimestamp), eq(request.toString()))
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(migrationUtility.isVaultSchemaAndTargetCpiInSync(cpkDbChangelogs, vaultDmlConnectionId)).thenReturn(true)
        whenever(virtualNodeRepository.completeOperation(em, request.virtualNodeShortHash)).thenReturn(migrationsCompleteVnodeInfo)

        val inProgressVNodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, "req1", request)

        verify(virtualNodeInfoPublisher, times(2)).publish(inProgressVNodeInfoCapture.capture())
        verify(migrationUtility, times(0)).runVaultMigrations(any(), any(), any())

        val publishedRecordList = inProgressVNodeInfoCapture.firstValue
        assertThat(publishedRecordList).isNotNull
        assertThat(publishedRecordList).hasSize(1)

        val publishedRecord = publishedRecordList[0]
        assertThat(publishedRecord.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        assertThat(publishedRecord.key.groupId).isEqualTo(groupName)
        assertThat(publishedRecord.key.x500Name).isEqualTo(x500Name.toString())

        assertThat(publishedRecord.value).isNotNull
        assertThat(publishedRecord.value!!.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(publishedRecord.value!!.cpiIdentifier.version).isEqualTo("v2")

        // todo cs - after we add operationInProgress to virtualNodeInfo, assertions here that after completion, it is null
    }
}