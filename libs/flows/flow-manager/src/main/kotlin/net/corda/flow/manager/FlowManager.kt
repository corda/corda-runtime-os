package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.P2PMessage
import net.corda.data.flow.event.Wakeup
import net.corda.v5.application.flows.StateMachineRunId
import org.apache.kafka.clients.producer.ProducerRecord

data class FlowResult(
    val checkpoint: Checkpoint,
    val events: List<ProducerRecord<String, ByteArray?>>
)

interface FlowManager {
    fun startInitiatingFlow(
        newFlowId: StateMachineRunId,
        clientId: String,
        flowName: String,
        args: List<Any?>,
    ): FlowResult

    fun startRemoteInitiatedFlow(
        newFlowId: StateMachineRunId,
        p2pMessage: P2PMessage,
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
        event: Wakeup,
    ): FlowResult
}
