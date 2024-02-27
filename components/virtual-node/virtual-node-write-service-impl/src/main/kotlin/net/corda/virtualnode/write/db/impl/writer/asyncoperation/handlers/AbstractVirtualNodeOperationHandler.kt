package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.Logger
import java.time.Instant

internal abstract class AbstractVirtualNodeOperationHandler(
    private val statusPublisher: Publisher,
    private val logger: Logger
) {
    protected fun publishStartProcessingStatus(requestId: String) {
        publishStatusMessage(requestId, getAvroStatusObject(requestId, VirtualNodeOperationStateDto.IN_PROGRESS))
    }

    protected fun publishProcessingCompletedStatus(requestId: String) {
        publishStatusMessage(requestId, getAvroStatusObject(requestId, VirtualNodeOperationStateDto.COMPLETED))
    }

    protected fun publishErrorStatus(requestId: String, reason: String) {
        val message = getAvroStatusObject(requestId, VirtualNodeOperationStateDto.UNEXPECTED_FAILURE)
        message.errors = reason
        publishStatusMessage(requestId, message)
    }

    private fun publishStatusMessage(requestId: String, message: VirtualNodeOperationStatus) {
        try {
            statusPublisher.publish(
                listOf(
                    Record(
                        Schemas.VirtualNode.VIRTUAL_NODE_OPERATION_STATUS_TOPIC,
                        requestId,
                        message
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to publish status update to Kafka for request ID = '$requestId'", e)
        }
    }

    private fun getAvroStatusObject(
        requestId: String,
        status: VirtualNodeOperationStateDto
    ): VirtualNodeOperationStatus {
        val now = Instant.now()
        return VirtualNodeOperationStatus.newBuilder()
            .setRequestId(requestId)
            .setRequestData("{}")
            .setRequestTimestamp(now)
            .setLatestUpdateTimestamp(now)
            .setHeartbeatTimestamp(null)
            .setState(status.name)
            .setErrors(null)
            .build()
    }
}
