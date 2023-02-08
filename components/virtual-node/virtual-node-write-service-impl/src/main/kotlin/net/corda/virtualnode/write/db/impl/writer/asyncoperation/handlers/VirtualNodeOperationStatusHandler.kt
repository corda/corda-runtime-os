package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.AsynchronousOperationState
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.use
import net.corda.schema.Schemas
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CompletableFuture

internal class VirtualNodeOperationStatusHandler(
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun handle(
        instant: Instant,
        operationStatusRequest: VirtualNodeOperationStatusRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        // Attempt and update, and on failure, pass the error back to the RPC processor
        try {
            val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()

            val requestId = operationStatusRequest.requestId

            logger.warn("VirtualNodeOperationStatusHandler.handle()")
            logger.warn("requestId: $requestId")

            val operationStatusResponse = virtualNodeRepository.findVirtualNodeOperation(em, requestId)

            logger.warn("operationStatusResponse operationStatusLite = ${operationStatusResponse.toString()}")

            val virtualNodeOperationStatus = VirtualNodeOperationStatus(
                operationStatusResponse.requestId,
                "shortHash",
                "actor",
                Object(),
                instant,
                instant,
                instant,
                AsynchronousOperationState.IN_PROGRESS,
                listOf()
                )

            logger.warn("created VirtualNodeOperationStatus")

            val response = VirtualNodeManagementResponse(
                instant,
                VirtualNodeOperationStatusResponse(
                    requestId,
                    listOf(virtualNodeOperationStatus)
                )
            )
            logger.warn("created VirtualNodeMnaagementResponse: ${response.responseType}")
            logger.warn("created VirtualNodeMnaagementResponse: $response")
            respFuture.complete(response)
        } catch (e: Exception) {
            logger.warn("handler exception ${e.message}")
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