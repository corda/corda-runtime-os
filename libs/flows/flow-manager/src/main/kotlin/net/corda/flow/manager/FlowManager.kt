package net.corda.flow.manager

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.data.flow.state.Checkpoint
import net.corda.sandbox.SandboxGroup

interface FlowManager {
    fun startInitiatingFlow(
        flowMetaData: FlowMetaData,
        clientId: String,
        sandboxGroup: SandboxGroup
    ): FlowResult

    fun startRemoteInitiatedFlow(
        flowMetaData: FlowMetaData,
        flowSessionMessage: FlowSessionMessage,
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
        wakeupEvent: FlowEvent,
        flowEventTopic: String,
        sandboxGroup: SandboxGroup,
    ): FlowResult
}
