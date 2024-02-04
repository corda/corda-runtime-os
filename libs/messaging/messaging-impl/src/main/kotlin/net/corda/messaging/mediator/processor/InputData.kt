package net.corda.messaging.mediator.processor

import java.time.Duration
import java.time.Instant

class InputData(val timestamp: Instant = Instant.now()) {
    val events = mutableListOf<EventData>()
    var totalTime = 0L
    class EventData(
        val event: String,
        val procTime: Long,
        val rpcCount: Int,
        val rpcOp: String,
        val rpcTime: Long,
    ) {
        override fun toString(): String {
            val proc = Duration.ofNanos(procTime)
            val rpc = Duration.ofNanos(rpcTime)
            return "event=$event, procTime=$proc, rpcCount=$rpcCount, rpcTime=$rpc, rpcOp=$rpcOp"
        }
    }

    override fun toString(): String {
        val total = Duration.ofNanos(totalTime)
        val event = events.joinToString { it.toString() }
        return "totalTime=$total events=[$event]"
    }
}