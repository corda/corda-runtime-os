package net.corda.cpi.upload.endpoints.v1

import net.corda.chunking.ChunkWriter
import net.corda.cpi.upload.endpoints.service.CpiUploadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.parseSecureHash
import net.corda.data.chunking.UploadStatus
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.rest.HttpFileUpload
import net.corda.rest.exception.InvalidInputDataException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.util.UUID

class CpiUploadRestResourceImplTest {
    private lateinit var cpiUploadRestResourceImpl: CpiUploadRestResourceImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var cpiUploadService: CpiUploadService
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
        cpiUploadService = mock()
        cpiInfoReadService = mock()
        cpiUploadRestResourceImpl = CpiUploadRestResourceImpl(coordinatorFactory, cpiUploadService, cpiInfoReadService)
        cpiUploadManager = mock()
        val mockStatus = mock<UploadStatus>()
        whenever(mockStatus.message).thenReturn(EXPECTED_MESSAGE)
        whenever(cpiUploadManager.status(any())).thenReturn(mockStatus)
        whenever(cpiUploadManager.status(UNKNOWN_REQUEST)).thenReturn(null)
        whenever(cpiUploadService.cpiUploadManager).thenReturn(cpiUploadManager)
    }

    @Test
    fun `returns request id mapping to a CPI uploading if the CPI was uploaded successfully to Kafka`() {
        val cpiBytes = "dummyCPI".toByteArray()
        val cpiContent = ByteArrayInputStream(cpiBytes)
        val cpiUploadRequestId = ChunkWriter.Request(UUID.randomUUID().toString(),
            parseSecureHash("FOO:123456789012")
        )

        whenever(cpiUploadManager.uploadCpi(any(), eq(cpiContent), eq(null))).thenReturn(cpiUploadRequestId)

        val httpResponse = cpiUploadRestResourceImpl.cpi(HttpFileUpload(cpiContent, DUMMY_FILE_NAME))
        assertNotNull(httpResponse)
        assertEquals(cpiUploadRequestId.requestId, httpResponse.id)
    }

    @Test
    fun `getAllCpis calls CpiInfoReadService to retrieve all CPIs`() {
        cpiUploadRestResourceImpl.getAllCpis()
        verify(cpiInfoReadService).getAll()
    }

    @Test
    fun `status returns 400 if invalid id is passed`() {
        cpiUploadRestResourceImpl.status("not unknown")
        assertThrows<InvalidInputDataException> {
            cpiUploadRestResourceImpl.status(UNKNOWN_REQUEST)
        }
    }
}
