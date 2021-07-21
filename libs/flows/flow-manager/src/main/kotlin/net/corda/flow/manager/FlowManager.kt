package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.sandbox.cache.FlowMetadata
import org.apache.kafka.clients.producer.ProducerRecord

data class FlowResult(
    val checkpoint: Checkpoint,
    val events: List<ProducerRecord<String, ByteArray?>>
)

interface FlowManager {
    fun startInitiatingFlow(
        newFlowMetadata: FlowMetadata,
        clientId: String,
        args: List<Any?>,
    ): FlowResult

    fun startRemoteInitiatedFlow(
        newFlowMetadata: FlowMetadata,
        flowSessionMessage: FlowSessionMessage,
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
    ): FlowResult
}
