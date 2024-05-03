package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.db.connection.manager.VirtualNodeDbType.CRYPTO
import net.corda.db.connection.manager.VirtualNodeDbType.UNIQUENESS
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.libs.external.messaging.ExternalMessagingRouteConfigGenerator
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.STATIC_NETWORK
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_OPERATION_STATUS_TOPIC
import net.corda.virtualnode.write.db.impl.tests.ALICE_HOLDING_ID1
import net.corda.virtualnode.write.db.impl.tests.CPI_CHECKSUM1
import net.corda.virtualnode.write.db.impl.tests.CPI_IDENTIFIER1
import net.corda.virtualnode.write.db.impl.tests.CPI_METADATA1
import net.corda.virtualnode.write.db.impl.tests.GROUP_POLICY1
import net.corda.virtualnode.write.db.impl.tests.getVNodeDb
import net.corda.virtualnode.write.db.impl.tests.getValidRequest
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.CreateVirtualNodeOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.CreateVirtualNodeService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture

class CreateVirtualNodeOperationHandlerTest {
    private val timestamp = Instant.now()
    private val requestId = "r1"

    private val vaultPlatformManagedVirtualNodeDb = getVNodeDb(VAULT, true)
    private val cryptoUserManagedVirtualNodeDb = getVNodeDb(CRYPTO, false)
    private val uniquenessPlatformManagedVirtualNodeDb = getVNodeDb(UNIQUENESS, true)
    private val externalMessagingRouteConfig = """{ "dummy": "dummy" }"""
    private val virtualNodeDbs = mapOf(
        VAULT to vaultPlatformManagedVirtualNodeDb,
        CRYPTO to cryptoUserManagedVirtualNodeDb,
        UNIQUENESS to uniquenessPlatformManagedVirtualNodeDb,
    )

    private val createVirtualNodeService = mock<CreateVirtualNodeService>().apply {
        whenever(validateRequest(any())).thenReturn(null)
        whenever(getCpiMetaData(CPI_CHECKSUM1.toString())).thenReturn(CPI_METADATA1)
    }

    private val externalMessagingRouteConfigGenerator = mock<ExternalMessagingRouteConfigGenerator>().apply {
        whenever(generateNewConfig(any(), any(), any())).thenReturn(externalMessagingRouteConfig)
    }

    private val virtualNodeDbFactory = mock<VirtualNodeDbFactory>().apply {
        whenever(createVNodeDbs(any(), any())).thenReturn(virtualNodeDbs)
    }

    private val recordFactory = mock<RecordFactory>()
    private val groupPolicyParser = mock<GroupPolicyParser>()
    private val statusPublisher = mock<Publisher>()

    private val target = CreateVirtualNodeOperationHandler(
        createVirtualNodeService,
        virtualNodeDbFactory,
        recordFactory,
        statusPublisher,
        externalMessagingRouteConfigGenerator,
        mock()
    )

    @Test
    fun `Handler validates request`() {
        val request = getValidRequest()
        whenever(createVirtualNodeService.validateRequest(any())).thenReturn("error")

        assertThrows<IllegalArgumentException> { target.handle(timestamp, requestId, request) }
    }

    @Test
    fun `Handler ensures holding ID is unique`() {
        val request = getValidRequest()

        target.handle(timestamp, requestId, request)

        verify(createVirtualNodeService).ensureHoldingIdentityIsUnique(request)
    }

    @Test
    fun `Handler only creates CPK DB objects for platform managed vault DBs`() {
        val request = getValidRequest()

        target.handle(timestamp, requestId, request)

        verify(createVirtualNodeService, times(1)).runCpiMigrations(
            CPI_METADATA1,
            vaultPlatformManagedVirtualNodeDb,
            ALICE_HOLDING_ID1
        )

        verify(createVirtualNodeService, never()).runCpiMigrations(
            any(),
            eq(uniquenessPlatformManagedVirtualNodeDb),
            any()
        )

        verify(createVirtualNodeService, never()).runCpiMigrations(
            any(),
            eq(cryptoUserManagedVirtualNodeDb),
            any()
        )
    }

    @Test
    fun `Handler creates DB objects when DB is managed by the platform`() {
        val request = getValidRequest()

        target.handle(timestamp, requestId, request)

        verify(vaultPlatformManagedVirtualNodeDb, times(1)).createSchemasAndUsers()
        verify(vaultPlatformManagedVirtualNodeDb, times(1))
            .runDbMigration("VAULT-system-final")
        verify(uniquenessPlatformManagedVirtualNodeDb, times(1)).createSchemasAndUsers()
        verify(uniquenessPlatformManagedVirtualNodeDb, times(1))
            .runDbMigration("VAULT-system-final")
    }

    @Test
    fun `Handler does not create DB objects when DB is not managed by the platform`() {
        val request = getValidRequest()

        target.handle(timestamp, requestId, request)

        verify(cryptoUserManagedVirtualNodeDb, never()).createSchemasAndUsers()
        verify(cryptoUserManagedVirtualNodeDb, never()).runDbMigration(any())
    }

    @Test
    fun `Handler persists the virtual node metadata`() {
        val request = getValidRequest()

        target.handle(timestamp, requestId, request)

        verify(createVirtualNodeService).persistHoldingIdAndVirtualNode(
            ALICE_HOLDING_ID1,
            virtualNodeDbs,
            CPI_IDENTIFIER1,
            request.updateActor,
            externalMessagingRouteConfig
        )
    }

    @Test
    fun `Handler publishes virtual info`() {
        val request = getValidRequest()
        val virtualNodeInfoRecord = Record("vnode", "", "")
        val dbConnections = mock<VirtualNodeDbConnections>()

        whenever(createVirtualNodeService.persistHoldingIdAndVirtualNode(any(), any(), any(), any(), any())).thenReturn(
            dbConnections
        )
        whenever(
            recordFactory.createVirtualNodeInfoRecord(
                ALICE_HOLDING_ID1,
                CPI_IDENTIFIER1,
                dbConnections,
                externalMessagingRouteConfig
            )
        ).thenReturn(
            virtualNodeInfoRecord
        )

        whenever(groupPolicyParser.getMgmInfo(ALICE_HOLDING_ID1, GROUP_POLICY1)).thenReturn(null)

        target.handle(timestamp, requestId, request)

        verify(createVirtualNodeService).publishRecords(listOf(virtualNodeInfoRecord))
    }

    @Test
    fun `Handler publishes status updates on success`() {
        val request = getValidRequest()
        val records = argumentCaptor<List<Record<Any, Any>>>()

        val f = CompletableFuture<Unit>().apply { this.complete(Unit) }
        whenever(statusPublisher.publish(records.capture())).thenReturn(listOf(f))

        target.handle(timestamp, requestId, request)

        records.firstValue.all { verifyRecord(it, requestId, "IN_PROGRESS") }
        records.secondValue.all { verifyRecord(it, requestId, "COMPLETED") }
    }

    @Test
    fun `Handler publishes status updates on failure`() {
        val request = getValidRequest()
        val records = argumentCaptor<List<Record<Any, Any>>>()

        val f = CompletableFuture<Unit>().apply { this.complete(Unit) }
        whenever(createVirtualNodeService.validateRequest(any())).thenReturn("error")
        whenever(statusPublisher.publish(records.capture())).thenReturn(listOf(f))

        assertThrows<IllegalArgumentException> { target.handle(timestamp, requestId, request) }

        records.firstValue.all { verifyRecord(it, requestId, "IN_PROGRESS") }
        records.secondValue.all { verifyRecord(it, requestId, "UNEXPECTED_FAILURE") }
    }

    private fun verifyRecord(record: Record<*, *>, requestId: String, status: String): Boolean {
        assertThat(record.topic).isEqualTo(VIRTUAL_NODE_OPERATION_STATUS_TOPIC)
        assertThat(record.key).isEqualTo(requestId)
        assertThat(record.value as VirtualNodeOperationStatus?).isNotNull
        val statusMessage = record.value as VirtualNodeOperationStatus
        assertThat(statusMessage.state).isEqualTo(status)
        return true
    }

    @Test
    fun `Handler doesn't try to handle MGM info if CPI is for a static network`() {
        whenever(createVirtualNodeService.getCpiMetaData(CPI_CHECKSUM1.toString()))
            .thenReturn(
                CpiMetadata(
                    CPI_IDENTIFIER1,
                    CPI_CHECKSUM1,
                    emptySet(),
                    """
                {"$PROTOCOL_PARAMETERS": { "$STATIC_NETWORK": {}}}
                    """.trimIndent(),
                    -1,
                    Instant.now()
                )
            )
        val request = getValidRequest()
        val virtualNodeInfoRecord = Record("vnode", "", "")
        val dbConnections = mock<VirtualNodeDbConnections>()

        whenever(createVirtualNodeService.persistHoldingIdAndVirtualNode(any(), any(), any(), any(), any())).thenReturn(
            dbConnections
        )
        whenever(
            recordFactory.createVirtualNodeInfoRecord(
                ALICE_HOLDING_ID1,
                CPI_IDENTIFIER1,
                dbConnections,
                externalMessagingRouteConfig
            )
        ).thenReturn(virtualNodeInfoRecord)

        target.handle(timestamp, requestId, request)

        verify(groupPolicyParser, never()).getMgmInfo(any(), any())
        verify(recordFactory, never()).createMgmInfoRecord(any(), any())
        verify(createVirtualNodeService).publishRecords(listOf(virtualNodeInfoRecord))
    }
}
