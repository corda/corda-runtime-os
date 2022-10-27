package net.corda.virtualnode.write.db.impl.writer.management

import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.virtualnode.VirtualNodeManagementResponse

interface VirtualNodeManagementHandler<REQUEST> {
    fun handle(requestTimestamp: Instant, request: REQUEST, respFuture: CompletableFuture<VirtualNodeManagementResponse>)
}