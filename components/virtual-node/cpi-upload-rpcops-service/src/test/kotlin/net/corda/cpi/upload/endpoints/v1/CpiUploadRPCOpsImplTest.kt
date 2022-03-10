package net.corda.cpi.upload.endpoints.v1

import net.corda.chunking.ChunkWriter
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.util.UUID

class CpiUploadRPCOpsImplTest {
    private lateinit var cpiUploadRPCOpsImpl: CpiUploadRPCOpsImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var cpiUploadManager: CpiUploadManager

    companion object {
        const val DUMMY_FILE_NAME = "dummyFileName"
    }

    private val cpiUploadRPCOpsService = mock<CpiUploadRPCOpsService>()
    private var cpiInfoReadService = mock<CpiInfoReadService>()

    @BeforeEach
    fun setUp() {
        val coordinator = mock<LifecycleCoordinator>().also {
           whenever(it.isRunning).thenReturn(true)
        }
        coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).thenReturn(coordinator)
        }
        cpiUploadRPCOpsImpl = CpiUploadRPCOpsImpl(coordinatorFactory, cpiUploadRPCOpsService, cpiInfoReadService)
        cpiUploadManager = mock()
        whenever(cpiUploadRPCOpsService.cpiUploadManager).thenReturn(cpiUploadManager)
    }

    @Test
    fun `returns request id mapping to a CPI uploading if the CPI was uploaded successfully to Kafka`() {
        val cpiBytes = "dummyCPI".toByteArray()
        val cpiContent = ByteArrayInputStream(cpiBytes)
        val cpiUploadRequestId = ChunkWriter.Request(UUID.randomUUID().toString(),
            SecureHash.create("FOO:123456789012"))

        whenever(cpiUploadManager.uploadCpi(any(), eq(cpiContent))).thenReturn(cpiUploadRequestId)

        val httpResponse = cpiUploadRPCOpsImpl.cpi(DUMMY_FILE_NAME, cpiContent)
        assertNotNull(httpResponse)
        assertEquals(cpiUploadRequestId.requestId, httpResponse.id)
    }

    @Test
    fun `getAllCpis calls CpiInfoReadService to retrieve all CPIs`() {
        val cpiInfoReadService = mock<CpiInfoReadService>()
        val rpcOps = CpiUploadRPCOpsImpl(mock(), mock(), cpiInfoReadService)
        rpcOps.getAllCpis()
        verify(cpiInfoReadService).getAll()
    }

}
