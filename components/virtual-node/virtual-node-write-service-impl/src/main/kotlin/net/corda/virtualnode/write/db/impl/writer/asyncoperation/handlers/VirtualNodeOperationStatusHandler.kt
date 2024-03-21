package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeCreateStatusResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.data.virtualnode.VirtualNodeUpdateDbStatusResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import java.time.Instant
import java.util.concurrent.CompletableFuture

internal class VirtualNodeOperationStatusHandler(
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
) {
    fun handle(
        instant: Instant,
        request: VirtualNodeOperationStatusRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        try {
            val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()

            val operationStatus = virtualNodeRepository.findVirtualNodeOperationByRequestId(em, request.requestId)

            val operationStatusesAvro = with(operationStatus) {
                VirtualNodeOperationStatus(
                    requestId,
                    requestData,
                    requestTimestamp,
                    latestUpdateTimestamp,
                    heartbeatTimestamp,
                    state,
                    errors
                )
            }

            val response: VirtualNodeManagementResponse =
                when (operationStatus.operationType) {
                    VirtualNodeOperationType.CHANGE_DB.name -> {
                        VirtualNodeManagementResponse(
                            instant,
                            VirtualNodeUpdateDbStatusResponse(
                                request.requestId,
                                VirtualNodeOperationType.CHANGE_DB.name,
                                operationStatusesAvro
                            )
                        )
                    }
                    VirtualNodeOperationType.CREATE.name -> {
                        VirtualNodeManagementResponse(
                            instant,
                            VirtualNodeCreateStatusResponse(
                                request.requestId,
                                VirtualNodeOperationType.CREATE.name,
                                operationStatusesAvro
                            )
                        )
                    }
                    else -> {
                        VirtualNodeManagementResponse(
                            instant,
                            VirtualNodeOperationStatusResponse(request.requestId, listOf(operationStatusesAvro))
                        )
                    }
                }
            respFuture.complete(response)
        } catch (e: Exception) {
            respFuture.complete(
                VirtualNodeManagementResponse(
                    instant,
                    VirtualNodeManagementResponseFailure(
                        ExceptionEnvelope(
                            e::class.java.name,
                            e.message
                        )
                    )
                )
            )
        }
    }
}
