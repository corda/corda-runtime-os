package net.corda.flow.fiber

import net.corda.data.flow.FlowStackItem
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import java.nio.ByteBuffer
import java.time.Instant

/**
 * A [FlowIORequest] represents an IO request of a flow when it suspends. It is persisted in checkpoints.
 */
interface FlowIORequest<out R> {
    /**
     * Send payloads to sessions.
     *
     * @property sessionToPayload a map from session to message-to-be-sent.
     */
    data class Send(val sessionToPayload: Map<String, ByteArray>) : FlowIORequest<Unit> {
        override fun toString() = "Send(sessionToMessage=${sessionToPayload.mapValues { it.value }})"
    }

    /**
     * Receive messages from sessions.
     *
     * @property sessions the sessions to receive payloads from.
     * @return a map from session to received payload.
     */
    data class Receive(val sessions: Set<String>) : FlowIORequest<Map<String, ByteArray>>

    /**
     * Send and receive payloads from the specified sessions.
     *
     * @property sessionToPayload a map from session to payload-to-be-sent. The keys also specify which sessions to receive from.
     * @return a map from session to received message.
     */
    data class SendAndReceive(val sessionToPayload: Map<String, ByteArray>) : FlowIORequest<Map<String, ByteArray>> {
        override fun toString() = "SendAndReceive(${sessionToPayload.mapValues { (key, value) -> "$key=$value" }})"
    }

    data class InitiateFlow(val x500Name: MemberX500Name, val sessionId: String) : FlowIORequest<Unit>

    /**
     * Closes the specified sessions.
     *
     * @property sessions the sessions to be closed.
     */
    data class CloseSessions(val sessions: Set<String>) : FlowIORequest<Unit>

    /**
     * Get the FlowInfo of the specified sessions.
     *
     * @property sessions the sessions to get the FlowInfo of.
     * @return a map from session to FlowInfo.
     */
    data class GetFlowInfo(val sessions: Set<FlowSession>) : FlowIORequest<Map<FlowSession, FlowInfo>>

    /**
     * Suspend the flow until the specified time.
     *
     * @property wakeUpAfter the time to sleep until.
     */
    data class Sleep(val wakeUpAfter: Instant) : FlowIORequest<Unit>

    /**
     * Suspend the flow until all Initiating sessions are confirmed.
     */
    object WaitForSessionConfirmations : FlowIORequest<Unit>

    /**
     * Indicates that no actual IO request occurred, and the flow should be resumed immediately.
     * This is used for performing explicit checkpointing anywhere in a flow.
     */
    // TODOs: consider using an empty FlowAsyncOperation instead
    object ForceCheckpoint : FlowIORequest<Unit>

    /**
     * The initial checkpoint capture point when a flow starts.
     */
    object InitialCheckpoint : FlowIORequest<Unit>

    data class FlowFinished(val result: String?) : FlowIORequest<String?>

    data class SubFlowFinished(val flowStackItem: FlowStackItem) : FlowIORequest<FlowStackItem?>

    data class SubFlowFailed(val throwable: Throwable, val flowStackItem: FlowStackItem) : FlowIORequest<Unit>

    data class FlowFailed(val exception: Throwable) : FlowIORequest<Unit>

    /**
     * Indicates a flow has been suspended
     * @property fiber serialized fiber state at the point of suspension.
     * @property output the IO request that caused the suspension.
     */
    data class FlowSuspended<SUSPENDRETURN>(val fiber: ByteBuffer, val output: FlowIORequest<SUSPENDRETURN>) : FlowIORequest<Unit>
}
