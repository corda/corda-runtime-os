package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

class CpiUploadRPCOpsImplTest {
    private lateinit var cpiUploadRPCOpsImpl: CpiUploadRPCOpsImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var cpiUploadManager: CpiUploadManager

    private val cpiUploadRPCOpsService = mock<CpiUploadRPCOpsService>()

    @BeforeEach
    fun setUp() {
        val coordinator = mock<LifecycleCoordinator>().also {
           whenever(it.isRunning).thenReturn(true)
        }
        coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).thenReturn(coordinator)
        }
        cpiUploadRPCOpsImpl = CpiUploadRPCOpsImpl(coordinatorFactory, cpiUploadRPCOpsService)
        cpiUploadManager = mock()
        whenever(cpiUploadRPCOpsService.cpiUploadManager).thenReturn(cpiUploadManager)
    }

    @Test
    fun `returns request id mapping to a CPI uploading if the CPI was uploaded successfully to Kafka`() {
        val cpiBytes = "dummyCPI".toByteArray()
        val cpiInputStream = ByteArrayInputStream(cpiBytes)
        val cpiUploadRequestId = UUID.randomUUID().toString()
        whenever(cpiUploadManager.uploadCpi(cpiInputStream)).thenReturn(cpiUploadRequestId)

        val httpResponse = cpiUploadRPCOpsImpl.cpi(cpiInputStream)
        assertNotNull(httpResponse)
        assertEquals(cpiUploadRequestId, httpResponse.id)
    }
}