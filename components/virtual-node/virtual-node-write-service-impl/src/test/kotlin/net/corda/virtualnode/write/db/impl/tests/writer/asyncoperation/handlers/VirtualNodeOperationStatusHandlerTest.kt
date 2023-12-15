package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationNotFoundException
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationDto
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeOperationStatusHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class VirtualNodeOperationStatusHandlerTest {

    private val em = mock<EntityManager> {
        on { transaction }.doReturn(mock())
    }
    private val emf = mock<EntityManagerFactory> {
        on { createEntityManager() }.doReturn(em)
    }
    private val connectionManager = mock<DbConnectionManager> {
        on { getClusterEntityManagerFactory() }.doReturn(emf)
    }
    private val virtualNodeRepository = mock<VirtualNodeRepository>()

    private val virtualNodeOperationStatusHandler = VirtualNodeOperationStatusHandler(connectionManager, virtualNodeRepository)

    private val requestId = UUID.randomUUID()
    private val virtualNodeOperationDto = VirtualNodeOperationDto(
        requestId.toString(),
        "requestData",
        "CREATE",
        Instant.now(),
        Instant.now(),
        Instant.now(),
        "IN_PROGRESS",
        null
    )

    @Test
    fun `OperationStatus handler returns VirtualNodeOperationStatusResponse if operation found`() {
        whenever(virtualNodeRepository.findVirtualNodeOperationByRequestId(em, requestId.toString()))
            .thenReturn(listOf(virtualNodeOperationDto))

        val respFuture = CompletableFuture<VirtualNodeManagementResponse>()

        virtualNodeOperationStatusHandler.handle(
            Instant.now(),
            VirtualNodeOperationStatusRequest(
                requestId.toString()
            ),
            respFuture
        )

        val response = respFuture.get().responseType
        val operationHistory = (response as VirtualNodeOperationStatusResponse).operationHistory

        assertThat(operationHistory[0].get("state")).isEqualTo("IN_PROGRESS")
        assertThat(operationHistory[0].get("requestData")).isEqualTo("requestData")
    }

    @Test
    fun `Handler returns VirtualNodeManagementResponseFailure if no operations found for request Id`() {
        whenever(virtualNodeRepository.findVirtualNodeOperationByRequestId(any(), eq(requestId.toString()))).thenThrow(
            VirtualNodeOperationNotFoundException(requestId.toString())
        )

        val respFuture = CompletableFuture<VirtualNodeManagementResponse>()

        virtualNodeOperationStatusHandler.handle(
            Instant.now(),
            VirtualNodeOperationStatusRequest(
                requestId.toString()
            ),
            respFuture
        )

        val response = respFuture.get().responseType
        val exception = (response as VirtualNodeManagementResponseFailure).exception

        assertThat(exception.errorMessage).isEqualTo("Could not find a virtual node operation with requestId of $requestId")
    }
}
