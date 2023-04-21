package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.db.connection.manager.DbConnectionManager
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

            val operationStatuses = virtualNodeRepository.findVirtualNodeOperationByRequestId(em, request.requestId)

            val operationStatusesAvro = operationStatuses.map {
                VirtualNodeOperationStatus(
                    it.requestId,
                    it.requestData,
                    it.requestTimestamp,
                    it.latestUpdateTimestamp,
                    it.heartbeatTimestamp,
                    it.state,
                    it.errors
                )
            }

            val response = VirtualNodeManagementResponse(
                instant,
                VirtualNodeOperationStatusResponse(request.requestId, operationStatusesAvro)
            )
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