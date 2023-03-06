package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.handlers

import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
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
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.LiquibaseDiffCheckFailedException

class VirtualNodeUpgradeOperationHandlerTest {
    private val oldVirtualNodeEntityRepository = mock<VirtualNodeEntityRepository>()
    private val virtualNodeInfoPublisher = mock<Publisher>()
    private val virtualNodeRepository = mock<VirtualNodeRepository>()
    private val entityTransaction = mock<EntityTransaction>()
    private val em = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val migrationUtility = mock<MigrationUtility>() {
        whenever(it.areChangesetsDeployedOnVault(any(), any(), any())).thenReturn(false)
    }
    private val vnodeId = "123456789011"

    private val mockChangelog1 = mock<CpkDbChangeLog> { changelog ->
        whenever(changelog.id).thenReturn(CpkDbChangeLogIdentifier(SecureHash("SHA-256","abc".toByteArray()), "cpk1"))
        whenever(changelog.content).thenReturn( "dog.xml")
    }
    private val mockChangelog2 = mock<CpkDbChangeLog> { changelog ->
        whenever(changelog.id).thenReturn(CpkDbChangeLogIdentifier(SecureHash("SHA-256","abc".toByteArray()),"cpk1"))
        whenever(changelog.content).thenReturn( "cat.xml")
    }
    private val cpkDbChangelogs = listOf(mockChangelog1, mockChangelog2)
    private val mockCpkDbChangeLogRepository = mock<CpkDbChangeLogRepository> {
        whenever(it.findByCpiId(any(), any())).thenReturn(cpkDbChangelogs)
    }

    private val handler = VirtualNodeUpgradeOperationHandler(
        entityManagerFactory,
        oldVirtualNodeEntityRepository,
        virtualNodeInfoPublisher,
        migrationUtility,
        mockCpkDbChangeLogRepository,
        virtualNodeRepository
    )

    private val sshBytes = ByteArray(16)
    private val ssh = SecureHash("SHA-256", sshBytes)
    private val sshString = ssh.toString()
    private val cpiName = "someCpi"
    private val cpiId = CpiIdentifier(cpiName,"v1", ssh)
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
    private val vaultDmlConnectionId = UUID.randomUUID()
    private val vaultDdlConnectionId = UUID.randomUUID()
    private val requestId = "req1"
    private val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)

    private val inProgressVnodeInfoWithoutVaultDdl = VirtualNodeInfo(
        mockHoldingIdentity,
        targetCpiId,
        null,
        vaultDmlConnectionId,
        null,
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        timestamp = Instant.now(),
        operationInProgress = requestId
    )

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
        operationInProgress = requestId,
        timestamp = Instant.now()
    )
    private val noInProgressOpVnodeInfo = VirtualNodeInfo(
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

    private fun withRejectedOperation(state: VirtualNodeOperationStateDto, reason: String, block: () -> Unit) {
        whenever(virtualNodeRepository.rejectedOperation(
            eq(em),
            eq(vnodeId),
            eq(requestId),
            eq(request.toString()),
            any(),
            eq(reason),
            eq(VirtualNodeOperationType.UPGRADE),
            eq(state)
        )).thenReturn(noInProgressOpVnodeInfo)

        block.invoke()
    }

    private fun withFailedOperation(state: VirtualNodeOperationStateDto, reason: String, block: () -> Unit) {
        whenever(virtualNodeRepository.failedOperation(
            eq(em),
            eq(vnodeId),
            eq(requestId),
            eq(request.toString()),
            any(),
            eq(reason),
            eq(VirtualNodeOperationType.UPGRADE),
            eq(state)
        )).thenReturn(noInProgressOpVnodeInfo)

        block.invoke()
    }

    @BeforeEach
    fun setUp() {
        whenever(em.transaction).thenReturn(entityTransaction).thenReturn(entityTransaction).thenReturn(entityTransaction)
        whenever(entityManagerFactory.createEntityManager()).thenReturn(em).thenReturn(em)
    }

    @Test
    fun `upgrade handler validates virtual node identifier is not null`() {
        assertThrows<IllegalArgumentException> {
            handler.handle(
                Instant.now(),
                requestId,
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
                requestId,
                VirtualNodeUpgradeRequest(
                    vnodeId, null, null
                )
            )
        }
    }

    @Test
    fun `upgrade handler validates it can find virtual node`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId)))
            .thenReturn(null)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Holding identity $vnodeId not found") {
            handler.handle(Instant.now(), requestId, request)
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

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Virtual node must be in maintenance") {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler validates there is no operation in progress`() {
        val vNode = mock<VirtualNodeInfo>() {
            whenever(it.cpiIdentifier).thenReturn(currentCpiId)
            whenever(it.flowOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
            whenever(it.flowStartOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
            whenever(it.flowP2pOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
            whenever(it.vaultDbOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
            whenever(it.operationInProgress).thenReturn("some-op")
        }
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(null)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Operation some-op already in progress") {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler can't find target CPI throws`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(null)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "CPI with file checksum $targetCpiChecksum was not found") {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler can't find current CPI associated with target CPI throws`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(null)

        withRejectedOperation(
            VirtualNodeOperationStateDto.VALIDATION_FAILED,
            "CPI with name ${targetCpiMetadata.id.name}, version v1 was not found"
        ) {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler validates target CPI and current CPI are in the same group`() {
        val cpiInDifferentGroup = mock<CpiMetadataLite> { whenever(it.mgmGroupId).thenReturn("group-b") }
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(cpiInDifferentGroup)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Expected MGM GroupId group-b but was someGroup1 in CPI") {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler fails to upgrade, rolls back transaction`() {
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(currentCpiMetadata)
        whenever(virtualNodeRepository.upgradeVirtualNodeCpi(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("err"))

        whenever(entityTransaction.rollbackOnly).thenReturn(true).thenReturn(false)

        withFailedOperation(VirtualNodeOperationStateDto.UNEXPECTED_FAILURE, "err") {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }

        verify(entityTransaction, times(1)).rollback()
    }

    @Test
    fun `upgrade handler successfully persists and publishes a single vnode info when no vault DDL provided`() {
        val requestTimestamp = Instant.now()

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(currentCpiMetadata)
        whenever(virtualNodeRepository.upgradeVirtualNodeCpi(
            eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString()))
        ).thenReturn(inProgressVnodeInfoWithoutVaultDdl)

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(1)).publish(vnodeInfoCapture.capture())

        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.firstValue, requestId)
    }

    @Test
    fun `migrations thrown an exception, operation is written with the details`() {
        val requestTimestamp = Instant.now()

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(migrationUtility.runVaultMigrations(any(), any(), any()))
            .thenThrow(VirtualNodeWriteServiceException("Outer exception", Exception("Inner exception")))

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        withFailedOperation(VirtualNodeOperationStateDto.MIGRATIONS_FAILED, "Inner exception") {
            handler.handle(requestTimestamp, requestId, request)
        }

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoCapture.capture())

        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.firstValue, requestId)
        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.secondValue, null)
    }

    @Test
    fun `liquibase diff checker fails with exception, operation is written for this failure`() {
        val requestTimestamp = Instant.now()
        whenever(
            migrationUtility.areChangesetsDeployedOnVault(
                any(),
                any(),
                any()
            )
        ).thenThrow(LiquibaseDiffCheckFailedException("outer error", java.lang.Exception("Inner error")))

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        withRejectedOperation(VirtualNodeOperationStateDto.LIQUIBASE_DIFF_CHECK_FAILED, "outer error") {
            handler.handle(requestTimestamp, requestId, request)
        }

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoCapture.capture())
        verify(migrationUtility, times(0)).runVaultMigrations(any(), any(), any())

        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.firstValue, requestId)
        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.secondValue, null)
    }

    @Test
    fun `upgrade handler successfully persists, runs migrations with vault ddl, publishes vnode info and completes operation`() {
        val requestTimestamp = Instant.now()

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(virtualNodeRepository.completedOperation(em, request.virtualNodeShortHash)).thenReturn(noInProgressOpVnodeInfo)

        val vnodeInfoRecordsCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoRecordsCapture.capture())
        verify(migrationUtility).runVaultMigrations(
            eq(ShortHash.of(request.virtualNodeShortHash)),
            eq(cpkDbChangelogs),
            eq(vaultDdlConnectionId)
        )

        assertUpgradedVnodeInfoIsPublished(vnodeInfoRecordsCapture.firstValue, requestId)
        assertUpgradedVnodeInfoIsPublished(vnodeInfoRecordsCapture.secondValue, null)
    }

    @Test
    fun `upgrade handler successfully persists, no migrations required`() {
        val requestTimestamp = Instant.now()
        val migrationUtility = mock<MigrationUtility>() {
            whenever(it.areChangesetsDeployedOnVault(any(), any(), any())).thenReturn(false)
        }

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(migrationUtility.areChangesetsDeployedOnVault(request.virtualNodeShortHash, cpkDbChangelogs, vaultDmlConnectionId))
            .thenReturn(true)
        whenever(virtualNodeRepository.completedOperation(em, request.virtualNodeShortHash)).thenReturn(noInProgressOpVnodeInfo)

        val vnodeInfoRecordsCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoRecordsCapture.capture())
        verify(migrationUtility, times(0)).runVaultMigrations(any(), any(), any())

        assertUpgradedVnodeInfoIsPublished(vnodeInfoRecordsCapture.firstValue, requestId)
        assertUpgradedVnodeInfoIsPublished(vnodeInfoRecordsCapture.secondValue, null)
    }

    private fun assertUpgradedVnodeInfoIsPublished(
        publishedRecordList: List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>,
        expectedOperationInProgress: String?
    ) {
        assertThat(publishedRecordList).isNotNull
        assertThat(publishedRecordList).hasSize(1)

        val publishedRecord = publishedRecordList[0]
        assertThat(publishedRecord.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        val holdingIdentity = publishedRecord.key
        assertThat(holdingIdentity.groupId).isEqualTo(groupName)
        assertThat(holdingIdentity.x500Name).isEqualTo(x500Name.toString())

        assertThat(publishedRecord.value).isNotNull
        val virtualNodeInfo = publishedRecord.value!!
        assertThat(virtualNodeInfo.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(virtualNodeInfo.cpiIdentifier.version).isEqualTo("v2")
        assertThat(virtualNodeInfo.operationInProgress).isEqualTo(expectedOperationInProgress)
    }
}