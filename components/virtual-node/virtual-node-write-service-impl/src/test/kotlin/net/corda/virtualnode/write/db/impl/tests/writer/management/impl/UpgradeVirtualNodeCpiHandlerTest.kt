package net.corda.virtualnode.write.db.impl.tests.writer.management.impl

import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.virtualnode.VirtualNodeCpiUpgradeRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.utilities.time.Clock
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.management.common.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.management.common.VirtualNodeInfoRecordPublisher
import net.corda.virtualnode.write.db.impl.writer.management.impl.UpgradeVirtualNodeCpiHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UpgradeVirtualNodeCpiHandlerTest {

    private val virtualNodeEntityRepository = mock<VirtualNodeEntityRepository>()
    private val dbConnectionsRepository = mock<DbConnectionsRepository>()
    private val vnodeDbFactory = mock<VirtualNodeDbFactory>()
    private val migrationUtility = mock<MigrationUtility>()
    private val virtualNodeInfoPublisher = mock<VirtualNodeInfoRecordPublisher>()
    private val clock = mock<Clock>()

    private val handler = UpgradeVirtualNodeCpiHandler(
        virtualNodeEntityRepository,
        dbConnectionsRepository,
        vnodeDbFactory,
        migrationUtility,
        virtualNodeInfoPublisher,
        clock
    )

    private val now = Instant.now()

    @Test
    fun `validate virtual node is in maintenance`() {
        val virtualNodeLite = VirtualNodeEntityRepository.VirtualNodeLite(
            "vnodeId", "cpiName", "cpiVer", "cpiSsh", "ACTIVE"
        )
        whenever(virtualNodeEntityRepository.findByHoldingIdentity("vnodeId"))
            .thenReturn(virtualNodeLite)
        val futureMock = mock<CompletableFuture<VirtualNodeManagementResponse>>()

        val responseCapture = argumentCaptor<VirtualNodeManagementResponse>()

        handler.handle(
            now,
            VirtualNodeCpiUpgradeRequest(
                "vnodeId",
                "a",
                "b"
            ),
            futureMock
        )

        verify(futureMock).complete(responseCapture.capture())
    }
}