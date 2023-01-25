package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import net.corda.messaging.api.records.Record
import java.time.Instant

interface VirtualNodeAsyncOperationHandler<REQUEST> {
    fun handle(requestTimestamp: Instant, requestId: String, request: REQUEST): Record<*, *>?
}