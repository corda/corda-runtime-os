package net.corda.flow.manager.fiber

import net.corda.data.flow.FlowStackItem
import net.corda.v5.application.flows.FlowInfo
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.serialization.SerializedBytes
import java.nio.ByteBuffer
import java.time.Instant

/**
 * A [FlowIORequest] represents an IO request of a flow when it suspends. It is persisted in checkpoints.
 */
interface FlowIORequest<out R> {
    /**
     * Send messages to sessions.
     *
     * @property sessionToMessage a map from session to message-to-be-sent.
     */
    data class Send(val sessionToMessage: Map<FlowSession, SerializedBytes<Any>>) : FlowIORequest<Unit> {
        override fun toString() = "Send(sessionToMessage=${sessionToMessage.mapValues { it.value }})"
    }

    /**
     * Receive messages from sessions.
     *
     * @property sessions the sessions to receive messages from.
     * @return a map from session to received message.
     */
    data class Receive(val sessions: Set<FlowSession>) : FlowIORequest<Map<FlowSession, SerializedBytes<Any>>>

    /**
     * Send and receive messages from the specified sessions.
     *
     * @property sessionToMessage a map from session to message-to-be-sent. The keys also specify which sessions to
     *     receive from.
     * @return a map from session to received message.
     */
    //net.corda.core.internal.FlowIORequest.SendAndReceive
    data class SendAndReceive(
        val sessionToMessage: Map<FlowSession, SerializedBytes<Any>>
    ) : FlowIORequest<Map<FlowSession, SerializedBytes<Any>>> {
        override fun toString() = "SendAndReceive(${sessionToMessage.mapValues { (key, value) -> "$key=$value" }})"
    }

    /**
     * Closes the specified sessions.
     *
     * @property sessions the sessions to be closed.
     */
    data class CloseSessions(val sessions: Set<FlowSession>) : FlowIORequest<Unit>

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

    data class FlowFinished(val result: Any?) : FlowIORequest<Any?>

    data class SubFlowFinished(val result: FlowStackItem?) : FlowIORequest<FlowStackItem?>

    data class SubFlowFailed(val exception: Throwable, val result: FlowStackItem?) : FlowIORequest<Unit>

    data class FlowFailed(val exception: Throwable) : FlowIORequest<Unit>

    /**
     * Indicates a flow has been suspended
     * @property fiber serialized fiber state at the point of suspension.
     * @property output the IO request that caused the suspension.
     */
    data class FlowSuspended<SUSPENDRETURN>(val fiber: ByteBuffer, val output: FlowIORequest<SUSPENDRETURN>) : FlowIORequest<Unit>
}
