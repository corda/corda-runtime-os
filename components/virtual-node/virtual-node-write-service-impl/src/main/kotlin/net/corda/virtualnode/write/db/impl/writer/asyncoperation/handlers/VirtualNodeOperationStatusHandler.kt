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
import java.time.Instant
import java.util.concurrent.CompletableFuture

internal class VirtualNodeOperationStatusHandler(
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
) {
    fun handle(
        instant: Instant,
        operationStatusRequest: VirtualNodeOperationStatusRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        // Attempt and update, and on failure, pass the error back to the RPC processor
        try {
            val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()

            val requestId = operationStatusRequest.requestId

            val operationStatusResponse = virtualNodeRepository.findVirtualNodeOperation(em, requestId)

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

//            val avroPayload = operationStatusResponse.toAvro()
//
//            val virtualNodeRecord = Record(
//                Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
//                avroPayload.holdingIdentity,
//                avroPayload
//            )

//            try {
//                // TODO - CORE-3319 - Strategy for DB and Kafka retries.
//                val future = vnodePublisher.publish(listOf(virtualNodeRecord)).first()
//
//                // TODO - CORE-3730 - Define timeout policy.
//                future.get()
//            } catch (e: Exception) {
//                throw VirtualNodeWriteServiceException(
//                    "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e", e
//                )
//            }

            val response = VirtualNodeManagementResponse(
                instant,
                VirtualNodeOperationStatusResponse(
                    requestId,
                    listOf(virtualNodeOperationStatus)
                )
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