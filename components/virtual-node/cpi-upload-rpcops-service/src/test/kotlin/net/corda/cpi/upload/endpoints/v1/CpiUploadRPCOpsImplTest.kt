package net.corda.cpi.upload.endpoints.v1

import net.corda.chunking.ChunkWriter
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.chunking.UploadStatus
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
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.exception.InvalidInputDataException
import org.junit.jupiter.api.assertThrows

class CpiUploadRPCOpsImplTest {
    private lateinit var cpiUploadRPCOpsImpl: CpiUploadRestResourceImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var cpiUploadRPCOpsService: CpiUploadRPCOpsService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var cpiUploadManager: CpiUploadManager

    companion object {
        const val DUMMY_FILE_NAME = "dummyFileName"
        const val UNKNOWN_REQUEST = "UNKNOWN_REQUEST"
        const val EXPECTED_MESSAGE = "EXPECTED_MESSAGE"
    }

    @BeforeEach
    fun setUp() {
        val coordinator = mock<LifecycleCoordinator>().also {
           whenever(it.isRunning).thenReturn(true)
        }
        coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).thenReturn(coordinator)
        }
        cpiUploadRPCOpsService = mock()
        cpiInfoReadService = mock()
        cpiUploadRPCOpsImpl = CpiUploadRestResourceImpl(coordinatorFactory, cpiUploadRPCOpsService, cpiInfoReadService)
        cpiUploadManager = mock()
        val mockStatus = mock<UploadStatus>()
        whenever(mockStatus.message).thenReturn(EXPECTED_MESSAGE)
        whenever(cpiUploadManager.status(any())).thenReturn(mockStatus)
        whenever(cpiUploadManager.status(UNKNOWN_REQUEST)).thenReturn(null)
        whenever(cpiUploadRPCOpsService.cpiUploadManager).thenReturn(cpiUploadManager)
    }

    @Test
    fun `returns request id mapping to a CPI uploading if the CPI was uploaded successfully to Kafka`() {
        val cpiBytes = "dummyCPI".toByteArray()
        val cpiContent = ByteArrayInputStream(cpiBytes)
        val cpiUploadRequestId = ChunkWriter.Request(UUID.randomUUID().toString(),
            SecureHash.parse("FOO:123456789012"))

        whenever(cpiUploadManager.uploadCpi(any(), eq(cpiContent), eq(null))).thenReturn(cpiUploadRequestId)

        val httpResponse = cpiUploadRPCOpsImpl.cpi(HttpFileUpload(cpiContent, DUMMY_FILE_NAME))
        assertNotNull(httpResponse)
        assertEquals(cpiUploadRequestId.requestId, httpResponse.id)
    }

    @Test
    fun `getAllCpis calls CpiInfoReadService to retrieve all CPIs`() {
        cpiUploadRPCOpsImpl.getAllCpis()
        verify(cpiInfoReadService).getAll()
    }

    @Test
    fun `status returns 400 if invalid id is passed`() {
        cpiUploadRPCOpsImpl.status("not unknown")
        assertThrows<InvalidInputDataException> {
            cpiUploadRPCOpsImpl.status(UNKNOWN_REQUEST)
        }
    }
}
