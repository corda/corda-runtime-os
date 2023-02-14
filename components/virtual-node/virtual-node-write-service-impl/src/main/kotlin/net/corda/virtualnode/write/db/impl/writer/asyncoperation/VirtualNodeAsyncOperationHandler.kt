package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import java.time.Instant

/**
 * Interface for handling asynchronous virtual node operations.
 */
interface VirtualNodeAsyncOperationHandler<REQUEST> {
    /**
     * Given a request of type [REQUEST], handle the request and return a record.
     *
     * @param requestTimestamp the instant the request was received
     * @param requestId the identifier of the request, used for traceability and logging purposes
     * @param request the request itself, each implemention should handle one type
     */
    fun handle(requestTimestamp: Instant, requestId: String, request: REQUEST)
}