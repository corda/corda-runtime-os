package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import net.corda.chunking.ChunkWriter
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.rest.HttpFileUpload
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.rest.security.RestAuthContext
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream

/** Tests of [VirtualNodeRPCOpsImpl]. */
class VirtualNodeMaintenanceRPCOpsImplTest {
    companion object {
        private const val actor = "test_principal"

        @Suppress("Unused")
        @JvmStatic
        @BeforeAll
        fun setRestContext() {
            val restAuthContext = mock<RestAuthContext>().apply {
                whenever(principal).thenReturn(actor)
            }
            CURRENT_REST_CONTEXT.set(restAuthContext)
        }
    }
    private val mockCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning) doReturn true
        whenever(status) doReturn LifecycleStatus.DOWN
    }
    private val mockCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        whenever(createCoordinator(any(), any())) doReturn mockCoordinator
    }

    @Nested
    inner class LifecycleTests {
        private val mockDownCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(isRunning) doReturn false
        }
        private val mockDownCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn mockDownCoordinator
        }
        @Test
        fun `verify coordinator is started on start`() {
            val vnodeMaintenanceRpcOps =
                VirtualNodeMaintenanceRestResourceImpl(mockCoordinatorFactory, mock(), mock(), mock())
            vnodeMaintenanceRpcOps.start()

            verify(mockCoordinator).start()
        }

        @Test
        fun `verify coordinator is stopped on stop`() {
            val vnodeMaintenanceRpcOps =
                VirtualNodeMaintenanceRestResourceImpl(mockCoordinatorFactory, mock(), mock(), mock())
            vnodeMaintenanceRpcOps.stop()

            verify(mockCoordinator).stop()
        }

        @Test
        fun `verify coordinator isRunning defers to the coordinator`() {
            val vnodeMaintenanceRpcOps =
                VirtualNodeMaintenanceRestResourceImpl(mockCoordinatorFactory, mock(), mock(), mock())
            vnodeMaintenanceRpcOps.isRunning

            verify(mockCoordinator).isRunning
            Assertions.assertTrue(vnodeMaintenanceRpcOps.isRunning)
        }

        @Test
        fun `verify exception throw if forceCpiUpload is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps =
                VirtualNodeMaintenanceRestResourceImpl(mockDownCoordinatorFactory, mock(), mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.forceCpiUpload(mock())
            }

            verify(mockDownCoordinator).isRunning
        }
    }

    @Nested
    inner class ServiceAPITests {
        val mockUpload = mock<HttpFileUpload>().apply {
            whenever(fileName) doReturn "test"
            whenever(content) doReturn InputStream.nullInputStream()
        }
        private val mockCpiResponse = mock<ChunkWriter.Request>().apply {
            whenever(requestId) doReturn "something"
        }
        private val mockCpiUploadManager = mock<CpiUploadManager>().apply {
            whenever(uploadCpi(any(), any(), any())) doReturn mockCpiResponse
        }
        private val mockCpiUploadRPCOpsService = mock<CpiUploadRPCOpsService>().apply {
            whenever(cpiUploadManager) doReturn mockCpiUploadManager
        }

        @Test
        fun `verify forceCpiUpload performs call to uploadCpi on cpiUploadManager`() {
            val vnodeRpcOps =
                VirtualNodeMaintenanceRestResourceImpl(mockCoordinatorFactory, mock(), mockCpiUploadRPCOpsService, mock())
            vnodeRpcOps.forceCpiUpload(mockUpload)

            verify(mockCpiUploadRPCOpsService.cpiUploadManager)
                .uploadCpi(
                    any(),
                    any(),
                    any()
                )
        }
    }
}
