package net.corda.flow.manager

import net.corda.v5.application.flows.StateMachineRunId
import org.apache.kafka.clients.producer.ProducerRecord


data class FlowTopics(
    val flowEventTopic: String,
    val checkpointsTopic: String,
    val p2pOutTopic: String,
    val rpcResponseTopic: String,
    val flowSessionMappingTopic: String
)

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
        topics: FlowTopics
    ): FlowResult

    fun startRemoteInitiatedFlow(
        newFlowId: StateMachineRunId,
        p2pMessage: FlowEvent.P2PMessage,
        topics: FlowTopics
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
        event: FlowEvent,
        topics: FlowTopics
    ): FlowResult
}
