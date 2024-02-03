package net.corda.messaging.mediator.processor

import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import java.util.concurrent.TimeUnit

data class EventMetrics(
    val topic: String,
    val metrics: EventMediatorMetrics
) {
    var processedCount = 0
    var rpcCount = 0L
    var stateDeserializeTime = 0L
    var stateCreateTime = 0L
    var procTime = 0L
    var proc1Time = 0L
    var rpcTime = 0L
    var sortTime = 0L
    var totalTime = 0L
    var taskTime = 0L
    var index = 0

    fun record() {
        metrics.timer(topic, "EVENT_STATE_DES_TIME").record(stateDeserializeTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_STATE_NEW_TIME").record(stateCreateTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_PROC_TIME").record(procTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_PROC1_TIME").record(proc1Time, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_SORT_TIME").record(sortTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_RPC_TIME").record(rpcTime, TimeUnit.NANOSECONDS)
        if (rpcCount > 0) {
            metrics.timer(topic, "EVENT_RPC_ONLY_TIME").record(rpcTime, TimeUnit.NANOSECONDS)
        }
        metrics.timer(topic, "EVENT_RPC_COUNT").record(rpcCount, TimeUnit.MILLISECONDS)
        metrics.timer(topic, "EVENT_TOTAL_TIME").record(totalTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "TASK$index").record(taskTime, TimeUnit.NANOSECONDS)

        metrics.timer(topic, "EVENT_STATE_DES_TIME$index").record(stateDeserializeTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_STATE_NEW_TIME$index").record(stateCreateTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_PROC_TIME$index").record(procTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_PROC1_TIME$index").record(proc1Time, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_SORT_TIME$index").record(sortTime, TimeUnit.NANOSECONDS)
        metrics.timer(topic, "EVENT_RPC_TIME$index").record(rpcTime, TimeUnit.NANOSECONDS)
        if (rpcCount > 0) {
            metrics.timer(topic, "EVENT_RPC_ONLY_TIME$index").record(rpcTime, TimeUnit.NANOSECONDS)
        }
        metrics.timer(topic, "EVENT_RPC_COUNT$index").record(rpcCount, TimeUnit.MILLISECONDS)
        metrics.timer(topic, "EVENT_TOTAL_TIME$index").record(totalTime, TimeUnit.NANOSECONDS)
    }
}