package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.messaging.api.records.Record
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow

data class FlowResult(
    val checkpoint: Checkpoint?,
    val events: List<Record<String, ByteArray>>
)

interface FlowManager {
    fun startInitiatingFlow(
        flow: Flow<*>,
        flowName: String,
        flowKey: FlowKey,
        clientId: String,
        sandboxGroup: SandboxGroup,
        args: List<Any?>,
    ): FlowResult

    fun startRemoteInitiatedFlow(
        flow: Flow<*>,
        flowSessionMessage: FlowSessionMessage,
    ): FlowResult

    fun wakeFlow(
        lastCheckpoint: Checkpoint,
    ): FlowResult
}
