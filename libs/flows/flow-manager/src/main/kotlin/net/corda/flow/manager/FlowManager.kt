package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.sandbox.cache.FlowId
import net.corda.v5.application.flows.FlowSession
import org.apache.kafka.clients.producer.ProducerRecord

data class FlowResult(
    val checkpoint: Checkpoint,
    val events: List<ProducerRecord<String, ByteArray?>>
)

interface FlowManager {
    fun startInitiatingFlow(
        newFlowId: FlowId,
        clientId: String,
        args: List<Any?>,
    ): FlowSession

    fun startRemoteInitiatedFlow(
        newFlowId: FlowId,
        flowSessionMessage: FlowSessionMessage,
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
    ): FlowResult
}
