package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.handlers

import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
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
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility

class VirtualNodeUpgradeOperationHandlerTest {
    private val oldVirtualNodeEntityRepository = mock<VirtualNodeEntityRepository>()
    private val virtualNodeInfoPublisher = mock<Publisher>()
    private val virtualNodeRepository = mock<VirtualNodeRepository>()
    private val entityTransaction = mock<EntityTransaction>()
    private val em = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val migrationUtility = mock<MigrationUtility>() {
        whenever(it.isVaultSchemaAndTargetCpiInSync(any(), any(), any())).thenReturn(false)
    }
    private val vnodeId = "123456789011"

    private val mockChangelog1 = mock<CpkDbChangeLog> { changelog ->
        whenever(changelog.id).thenReturn(CpkDbChangeLogIdentifier("", "cpk1"))
        whenever(changelog.content).thenReturn( "dog.xml")
    }
    private val mockChangelog2 = mock<CpkDbChangeLog> { changelog ->
        whenever(changelog.id).thenReturn(CpkDbChangeLogIdentifier("","cpk1"))
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
        operationInProgress = "someOperationId",
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
    private val requestId = "req1"
    private val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null)

    private fun withUpgradeValidationFailure(reason: String, block: () -> Any?) {
        val result = block.invoke()
        verify(virtualNodeRepository, times(1)).rejectedOperation(
            eq(em), eq(vnodeId), eq(requestId), eq(request.toString()), any(), eq(reason), eq(VirtualNodeOperationType.UPGRADE)
        )
        assertThat(result).isNull()
    }

    private fun withMigrationFailure(reason: String, block: () -> Any?) {
        val result = block.invoke()
        verify(virtualNodeRepository, times(1)).failedMigrationsOperation(
            eq(em), eq(vnodeId), eq(requestId), eq(request.toString()), any(), eq(reason), eq(VirtualNodeOperationType.UPGRADE)
        )
        assertThat(result).isNull()
    }

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

        withUpgradeValidationFailure("Holding identity $vnodeId not found") {
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

        withUpgradeValidationFailure("Virtual node must be in maintenance") {
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

        withUpgradeValidationFailure("Operation some-op already in progress") {
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

        withUpgradeValidationFailure("CPI with file checksum $targetCpiChecksum was not found") {
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
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(null)

        withUpgradeValidationFailure("CPI with name ${targetCpiMetadata.id.name}, version v1 was not found") {
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
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(cpiInDifferentGroup)

        withUpgradeValidationFailure("Expected MGM GroupId group-b but was someGroup1 in CPI") {
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
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(virtualNodeRepository.upgradeVirtualNodeCpi(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("err"))

        whenever(entityTransaction.rollbackOnly).thenReturn(true)

        assertThrows<IllegalArgumentException> {
            handler.handle(
                Instant.now(),
                requestId,
                request
            )
        }

        verify(entityTransaction, times(1)).rollback()
    }

    @Test
    fun `upgrade handler successfully persists and publishes vnode info`() {
        val requestTimestamp = Instant.now()

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(vnodeInfoWithoutVaultDdl)

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

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
    fun `migrations thrown an exception, operation is written with the details`() {
        val requestTimestamp = Instant.now()

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
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

        withMigrationFailure("Inner exception") {
            handler.handle(requestTimestamp, requestId, request)
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

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(virtualNodeRepository.completeOperation(em, request.virtualNodeShortHash)).thenReturn(migrationsCompleteVnodeInfo)

        val vnodeInfoRecordsCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoRecordsCapture.capture())
        verify(migrationUtility).runVaultMigrations(
            eq(ShortHash.of(request.virtualNodeShortHash)),
            eq(cpkDbChangelogs),
            eq(vaultDdlConnectionId)
        )

        assertSuccessfulVirtualNodeInfoPublishing(vnodeInfoRecordsCapture.firstValue, vnodeInfoRecordsCapture.secondValue)
    }

    @Test
    fun `upgrade handler successfully persists, no migrations required`() {
        val requestTimestamp = Instant.now()
        val migrationUtility = mock<MigrationUtility>() {
            whenever(it.isVaultSchemaAndTargetCpiInSync(any(), any(), any())).thenReturn(false)
        }

        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(targetCpiMetadata)
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(eq(em), eq(cpiName), eq("v1"), eq(sshString)))
            .thenReturn(currentCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em), eq(vnodeId), eq(cpiName), eq("v2"), eq(sshString), eq(requestId), eq(requestTimestamp), eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
        whenever(migrationUtility.isVaultSchemaAndTargetCpiInSync(request.virtualNodeShortHash, cpkDbChangelogs, vaultDmlConnectionId))
            .thenReturn(true)
        whenever(virtualNodeRepository.completeOperation(em, request.virtualNodeShortHash)).thenReturn(migrationsCompleteVnodeInfo)

        val vnodeInfoRecordsCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoRecordsCapture.capture())
        verify(migrationUtility, times(0)).runVaultMigrations(any(), any(), any())

        assertSuccessfulVirtualNodeInfoPublishing(vnodeInfoRecordsCapture.firstValue, vnodeInfoRecordsCapture.secondValue)
    }

    private fun assertSuccessfulVirtualNodeInfoPublishing(
        inProgressRecord: List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>,
        completedRecord: List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>
    ) {
        assertThat(inProgressRecord).isNotNull
        assertThat(inProgressRecord).hasSize(1)

        val preMigrationPublish = inProgressRecord[0]
        assertThat(preMigrationPublish.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        assertThat(preMigrationPublish.key.groupId).isEqualTo(groupName)
        assertThat(preMigrationPublish.key.x500Name).isEqualTo(x500Name.toString())

        val preMigrationVirtualNodeInfo = preMigrationPublish.value
        assertThat(preMigrationVirtualNodeInfo).isNotNull
        assertThat(preMigrationVirtualNodeInfo!!.cpiIdentifier.version).isEqualTo("v2")
        assertThat(preMigrationVirtualNodeInfo.cpiIdentifier.version).isEqualTo("v2")
        assertThat(preMigrationVirtualNodeInfo.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(preMigrationVirtualNodeInfo.operationInProgress).isNotNull
        assertThat(preMigrationVirtualNodeInfo.operationInProgress).isEqualTo("someOperationId")

        assertThat(completedRecord).isNotNull
        assertThat(completedRecord).hasSize(1)

        val postMigrationPublish = completedRecord[0]
        assertThat(postMigrationPublish.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        assertThat(postMigrationPublish.key.groupId).isEqualTo(groupName)
        assertThat(postMigrationPublish.key.x500Name).isEqualTo(x500Name.toString())

        val postMigrationVnodeInfo = postMigrationPublish.value
        assertThat(postMigrationVnodeInfo).isNotNull
        assertThat(postMigrationVnodeInfo!!.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(postMigrationVnodeInfo.cpiIdentifier.version).isEqualTo("v2")
        assertThat(postMigrationVnodeInfo.operationInProgress).isNull()
    }
}
