package net.corda.v5.application.messaging

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

@DoNotImplement
interface FlowMessaging {

    /**
     * Creates a communication session with [party]. Subsequently you may send/receive using this session object. Note
     * that this function does not communicate in itself, the counter-flow will be kicked off by the first send/receive.
     */
    @Suspendable
    fun initiateFlow(x500Name: MemberX500Name): FlowSession

    /** Suspends until a message has been received for each session in the specified [sessions].
     *
     * Consider [receiveAll(receiveType: Class<R>, sessions: Set<FlowSession>): List<UntrustworthyData<R>>] when the same type is expected from all sessions.
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * @returns a [Map] containing the objects received, wrapped in an [UntrustworthyData], by the [FlowSession]s who sent them.
     */
    @Suspendable
    fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>>

    /**
     * Suspends until a message has been received for each session in the specified [sessions].
     *
     * Consider [sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>>] when sessions are expected to receive different types.
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * @returns a [List] containing the objects received, wrapped in an [UntrustworthyData], with the same order of [sessions].
     */
    @Suspendable
    fun <R> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<UntrustworthyData<R>>

    /**
     * Queues the given [payload] for sending to the provided [sessions] and continues without suspending.
     *
     * Note that the other parties may receive the message at some arbitrary later point or not at all: if one of the provided [sessions]
     * is offline then message delivery will be retried until the corresponding node comes back or until the message is older than the
     * network's event horizon time.
     *
     * @param payload the payload to send.
     * @param sessions the sessions to send the provided payload to.
     */
    @Suspendable
    fun sendAll(payload: Any, sessions: Set<FlowSession>)

    /**
     * Queues the given payloads for sending to the provided sessions and continues without suspending.
     *
     * Note that the other parties may receive the message at some arbitrary later point or not at all: if one of the provided [sessions]
     * is offline then message delivery will be retried until the corresponding node comes back or until the message is older than the
     * network's event horizon time.
     *
     * @param payloadsPerSession a mapping that contains the payload to be sent to each session.
     */
    @Suspendable
    fun sendAllMap(payloadsPerSession: Map<FlowSession, *>)

    /**
     * Closes the provided sessions and performs cleanup of any resources tied to these sessions.
     *
     * Note that sessions are closed automatically when the corresponding top-level flow terminates.
     * So, it's beneficial to eagerly close them in long-lived flows that might have many open sessions that are not needed anymore and consume resources (e.g. memory, disk etc.).
     * A closed session cannot be used anymore, e.g. to send or receive messages. So, you have to ensure you are calling this method only when the provided sessions are not going to be used anymore.
     * As a result, any operations on a closed session will fail with an [UnexpectedFlowEndException].
     * When a session is closed, the other side is informed and the session is closed there too eventually.
     * To prevent misuse of the API, if there is an attempt to close an uninitialised session the invocation will fail with an [IllegalStateException].
     */
    @Suspendable
    fun close(sessions: Set<FlowSession>)
}
