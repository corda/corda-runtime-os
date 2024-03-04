package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.orm.utils.transaction
import java.time.Instant
import javax.persistence.EntityManagerFactory

internal abstract class AbstractVirtualNodeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl()
) {
    protected fun publishStartProcessingStatus(requestId: String, virtualNodeOperationType: VirtualNodeOperationType) {
        publishStatusMessage(
            requestId,
            getAvroStatusObject(requestId, VirtualNodeOperationStateDto.IN_PROGRESS),
            virtualNodeOperationType.name
        )
    }

    protected fun publishProcessingCompletedStatus(
        requestId: String,
        virtualNodeOperationType: VirtualNodeOperationType
    ) {
        publishStatusMessage(
            requestId,
            getAvroStatusObject(requestId, VirtualNodeOperationStateDto.COMPLETED),
            virtualNodeOperationType.name
        )
    }

    protected fun publishErrorStatus(
        requestId: String,
        reason: String,
        virtualNodeOperationType: VirtualNodeOperationType
    ) {
        val message = getAvroStatusObject(requestId, VirtualNodeOperationStateDto.UNEXPECTED_FAILURE)
        message.errors = reason
        publishStatusMessage(requestId, message, virtualNodeOperationType.name)
    }

    private fun publishStatusMessage(requestId: String, message: VirtualNodeOperationStatus, operationType: String) {
        recordVirtualNodeOperation(
            requestId = requestId,
            requestTimestamp = message.requestTimestamp,
            requestData = message.requestData,
            errors = message.errors,
            operationStateString = message.state,
            operationType = operationType
        )
    }

    @Suppress("LongParameterList")
    fun recordVirtualNodeOperation(
        requestId: String,
        requestTimestamp: Instant,
        requestData: String,
        errors: String? = null,
        operationStateString: String,
        operationType: String
    ) =
        entityManagerFactory.createEntityManager().transaction { em ->
            val latestUpdateTimestamp = Instant.now()
            virtualNodeRepository.putVirtualNodeOperation(
                em,
                VirtualNodeOperationDto(
                    requestId = requestId,
                    requestData = requestData,
                    operationType = operationType,
                    requestTimestamp = requestTimestamp,
                    latestUpdateTimestamp = latestUpdateTimestamp,
                    heartbeatTimestamp = latestUpdateTimestamp,
                    state = operationStateString,
                    errors = errors
                )
            )
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
