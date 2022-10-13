package net.corda.flow.fiber

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.types.MemberX500Name

/**
 * A [FlowIORequest] represents an IO request of a flow when it suspends. It is persisted in checkpoints.
 */
interface FlowIORequest<out R> {
    /**
     * Send payloads to sessions.
     *
     * @property sessionPayloads map of data packets to send to counterparties
     */
    data class Send(
        val sessionPayloads: Map<SessionInfo, ByteArray>,
    ) : FlowIORequest<Unit> {
        override fun toString() = "Send(payloads=${sessionPayloads.keys.joinToString { it.toString() }})"
    }

    /**
     * Information about a session
     * @property sessionId Id of the session
     * @property counterparty x500 name of the counterparty
     * @property contextUserProperties user context properties used in session initiation only
     * @property contextPlatformProperties platform context properties used in session initiation only
     */
    data class SessionInfo(
        val sessionId: String,
        val counterparty: MemberX500Name,
        val contextUserProperties: Map<String, String> = emptyMap(),
        val contextPlatformProperties: Map<String, String> = emptyMap()
    ) {
        override fun toString() = "SessionInfo(sessionId=$sessionId, counterparty=$counterparty) "
    }

    /**
     * Receive messages from sessions.
     *
     * @property sessions the sessions to receive payloads from.
     * @return a map from session to received payload.
     */
    data class Receive(val sessions: Set<SessionInfo>) : FlowIORequest<Map<String, ByteArray>>

    /**
     * Send and receive payloads from the specified sessions.
     *
     * @property sessionToInfo Map of data packets to send to counterparties
     * @return a map from session to received message.
     */
    data class SendAndReceive(
        val sessionToInfo: Map<SessionInfo, ByteArray>,
    ) : FlowIORequest<Map<String, ByteArray>> {
        override fun toString() = "SendAndReceive(${sessionToInfo.keys.joinToString { it.toString() }})"
    }

    /**
     * Closes the specified sessions.
     *
     * @property sessions the sessions to be closed.
     */
    data class CloseSessions(val sessions: Set<String>) : FlowIORequest<Unit>

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

    data class SubFlowFinished(val sessionIds: List<String>) : FlowIORequest<Unit>

    data class SubFlowFailed(val throwable: Throwable, val sessionIds: List<String>) : FlowIORequest<Unit>

    data class FlowFailed(val exception: Throwable) : FlowIORequest<Unit>

    /**
     * Indicates a flow has been suspended
     * @property fiber serialized fiber state at the point of suspension.
     * @property output the IO request that caused the suspension.
     */
    data class FlowSuspended<SUSPENDRETURN>(
        val fiber: ByteBuffer,
        val output: FlowIORequest<SUSPENDRETURN>
    ) : FlowIORequest<Unit>

    data class ExternalEvent(
        val requestId: String,
        val factoryClass: Class<out ExternalEventFactory<out Any, *, *>>,
        val parameters: Any,
        val contextProperties: Map<String, String>
    ) : FlowIORequest<Any>
}
