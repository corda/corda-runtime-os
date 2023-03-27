package net.corda.virtualnode.rest.converters.impl

import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.virtualnode.rest.converters.MessageConverter
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus

class MessageConverterImpl : MessageConverter {

    // Avro Message states
    companion object {
        const val ACCEPTED = "ACCEPTED"
        const val IN_PROGRESS = "IN_PROGRESS"
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
        const val LIQUIBASE_DIFF_CHECK_FAILED = "LIQUIBASE_DIFF_CHECK_FAILED"
        const val MIGRATIONS_FAILED = "MIGRATIONS_FAILED"
        const val UNEXPECTED_FAILURE = "UNEXPECTED_FAILURE"
        const val COMPLETED = "COMPLETED"
        const val ABORTED = "ABORTED"
    }


    override fun convert(
        status: AvroVirtualNodeOperationStatus,
        operation: String,
        resourceId: String?
    ): AsyncOperationStatus {

        return when (status.state) {
            ACCEPTED -> {
                AsyncOperationStatus.accepted(status.requestId,operation, status.latestUpdateTimestamp)
            }

            IN_PROGRESS -> {
                AsyncOperationStatus.inProgress(status.requestId,operation, status.latestUpdateTimestamp)
            }

            VALIDATION_FAILED -> {
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors,
                    "Validation"
                )
            }

            LIQUIBASE_DIFF_CHECK_FAILED ->{
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors,
                    "Liquibase diff check"
                )
            }

            MIGRATIONS_FAILED ->{
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors,
                    "Migration"
                )
            }

            UNEXPECTED_FAILURE ->{
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors
                )
            }

            ABORTED ->{
                AsyncOperationStatus.aborted(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp
                )
            }

            COMPLETED ->{
                AsyncOperationStatus.succeeded(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    resourceId
                )
            }

            else ->{
                throw IllegalStateException("The virtual node operation status '${status.state}' is not recognized.")
            }
        }
    }
}