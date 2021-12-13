package net.corda.v5.application.flows

import net.corda.v5.application.flows.flowservices.FlowMessaging
import net.corda.v5.application.identity.AnonymousParty
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable

/**
 * A [FlowSession] is a handle on a communication sequence between two paired flows, possibly running on separate nodes.
 *
 * It is used to send and receive messages between the flows as well as to query information about the counter-flow.
 *
 * There are two ways of obtaining such a session:
 *
 * 1. Calling [FlowMessaging.initiateFlow]. This will create a [FlowSession] object on which the first send/receive operation will attempt
 *    to kick off a corresponding [InitiatedBy] flow on the counterparty's node.
 *
 * 2. As constructor parameter to [InitiatedBy] flows. This session is the one corresponding to the initiating flow and may be used for
 *    replies.
 */
@DoNotImplement
interface FlowSession {
    /**
     * The [Destination] on the other side of this session. In the case of a session created by [FlowMessaging.initiateFlow] this is the
     * same destination as the one passed to that function.
     */
    val destination: Destination

    /**
     * If the destination on the other side of this session is a [Party] then returns that, otherwise throws [IllegalStateException].
     *
     * Only use this method if it's known the other side is a [Party], otherwise use [destination].
     *
     * @throws IllegalStateException if the other side is not a [Party].
     * @see destination
     */
    val counterparty: Party

    /**
     * Returns a [FlowInfo] object describing the flow [counterparty] is using. With [FlowInfo.flowVersion] it provides the necessary
     * information needed for the evolution of flows and enabling backwards compatibility.
     *
     * This method can be called before any send or receive has been done with [counterparty]. In such a case this will force them to start
     * their flow.
     */
    @Suspendable
    fun getCounterpartyFlowInfo(): FlowInfo

    /**
     * Serializes and queues the given [payload] object for sending to the [counterparty]. Suspends until a response is received, which must
     * be of the given [receiveType]. Remember that when receiving data from other parties the data should not be trusted until it's been
     * thoroughly verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly corrupted data
     * in order to exploit your code.
     *
     * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to use this when you expect to
     * do a message swap than do use [send] and then [receive] in turn.
     *
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R>


    /**
     * Suspends until [counterparty] sends us a message of type [receiveType].
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly verified for
     * consistency and that all expectations are satisfied, as a malicious peer may send you subtly corrupted data in order to exploit your
     * code.
     *
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R>


    /**
     * Queues the given [payload] for sending to the [counterparty] and continues without suspending.
     *
     * Note that the other party may receive the message at some arbitrary later point or not at all: if [counterparty] is offline then
     * message delivery will be retried until it comes back or until the message is older than the network's event horizon time.
     */
    @Suspendable
    fun send(payload: Any)

    /**
     * Closes this session and performs cleanup of any resources tied to this session.
     *
     * Note that sessions are closed automatically when the corresponding top-level flow terminates.
     *
     * So, it's beneficial to eagerly close them in long-lived flows that might have many open sessions that are not needed anymore and
     * consume resources (e.g. memory, disk etc.).
     *
     * A closed session cannot be used anymore, e.g. to send or receive messages. So, you have to ensure you are calling this method only
     * when the session is not going to be used anymore. As a result, any operations on a closed session will fail with an
     * [UnexpectedFlowEndException].
     *
     * When a session is closed, the other side is informed and the session is closed there too eventually.
     *
     * To prevent misuse of the API, if there is an attempt to close an uninitialised session the invocation will fail with an
     * [IllegalStateException].
     */
    @Suspendable
    fun close()
}

/**
 * Serializes and queues the given [payload] object for sending to the counterparty. Suspends until a response is received, which must be of
 * the given [R] type.
 *
 * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly verified for consistency
 * and that all expectations are satisfied, as a malicious peer may send you subtly corrupted data in order to exploit your code.
 *
 * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to use this when you expect to do a
 * message swap than do use [FlowSession.send] and then [FlowSession.receive] in turn.
 *
 * @return an [UntrustworthyData] wrapper around the received object.
 */
@Suspendable
inline fun <reified R : Any> FlowSession.sendAndReceive(payload: Any): UntrustworthyData<R> {
    return sendAndReceive(R::class.java, payload)
}

/**
 * Suspends until the counterparty sends us a message of type [R].
 *
 * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly verified for consistency
 * and that all expectations are satisfied, as a malicious peer may send you subtly corrupted data in order to exploit your code.
 */
@Suspendable
inline fun <reified R : Any> FlowSession.receive(): UntrustworthyData<R> {
    return receive(R::class.java)
}


/**
 * An abstraction for flow session destinations. A flow can send to and receive from objects which implement this interface. The specifics
 * of how the messages are routed depend on the implementation.
 *
 * Corda currently only supports a fixed set of destination types, namely [Party] and [AnonymousParty]. New destination types will be added
 * in future releases.
 */
@DoNotImplement
interface Destination
