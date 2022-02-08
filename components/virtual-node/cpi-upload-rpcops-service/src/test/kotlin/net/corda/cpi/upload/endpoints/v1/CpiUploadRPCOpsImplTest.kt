package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.cpiupload.impl.CpiUploadManagerImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.util.*

class CpiUploadRPCOpsImplTest {
    private lateinit var cpiUploadRPCOpsImpl: CpiUploadRPCOpsImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory

    private val cpiUploadManager = CpiUploadManagerImpl(mock(), mock())
    private val cpiUploadRPCOpsService = mock<CpiUploadRPCOpsService>().also {
        whenever(it.cpiUploadManager).thenReturn(cpiUploadManager)
    }

    @BeforeEach
    fun setUp() {
        val coordinator = mock<LifecycleCoordinator>()
        whenever(coordinator.status).thenReturn(LifecycleStatus.UP)

        coordinatorFactory = mock()
        whenever(coordinatorFactory.createCoordinator(any(), any())).thenReturn(coordinator)

        cpiUploadRPCOpsImpl = CpiUploadRPCOpsImpl(coordinatorFactory, cpiUploadRPCOpsService)
    }

    @Test
    fun ` returns request id mapping to a CPI uploading if the CPI was uploaded to Kafka successfully`() {
        val bytes = "dummyCPI".toByteArray()
        val httpResponse = cpiUploadRPCOpsImpl.cpi(ByteArrayInputStream(bytes))
        assertNotNull(httpResponse)
        assertDoesNotThrow {
            UUID.fromString(httpResponse.id)
        }
    }
}