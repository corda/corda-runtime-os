package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer

interface FlowManager {
    fun startInitiatingFlow(
        flowMetaData: FlowMetaData,
        clientId: String,
        sandboxGroup: SandboxGroup,
        checkpointSerializer: CheckpointSerializer
    ): FlowResult

    fun startRemoteInitiatedFlow(
        flowMetaData: FlowMetaData,
        flowSessionMessage: FlowSessionMessage,
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
        wakeupEvent: FlowEvent,
        flowEventTopic: String,
        checkpointSerializer: CheckpointSerializer,
    ): FlowResult
}
