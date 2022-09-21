@file:JvmName("FlowSessionUtils")
package net.corda.v5.application.messaging

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

/**
 * A [FlowSession] is a handle on a communication sequence between two paired flows, possibly running on separate nodes.
 *
 * It is used to send and receive messages between the flows as well as to query information about the counter-flow.
 * Sessions have their own local flow context which can be accessed via the [contextProperties] property. Note that the
 * parent context is snapshotted at the point the [contextProperties] is first accessed, after which no other changes to
 * the parent context will be reflected in them. See [contextProperties] for more information.
 *
 * There are two ways of obtaining such a session:
 *
 * 1. Calling [FlowMessaging.initiateFlow]. This will create a [FlowSession] object on which the first send/receive
 * operation will attempt to kick off a corresponding [ResponderFlow] flow on the counterpart's node.
 *
 * 2. As constructor parameter to [ResponderFlow] flows. This session is the one corresponding to the initiating flow
 * and may be used for replies.
 *
 * @see FlowMessaging
 */
@DoNotImplement
interface FlowSession {

    /**
     * The [MemberX500Name] of the counterparty this session is connected to.
     */
    val counterparty: MemberX500Name

    /**
     * Session local [FlowContextProperties].
     *
     * If this session is part of an initiating flow, i.e. was obtained from [FlowMessaging] then this is a read only
     * set of context properties which will be used to determine context on the initiated side. Modifying this set is
     * only possible when session are initiated, see [FlowMessaging].
     *
     * If this session was passed to an initiated flow by Corda, the context properties are associated with the
     * initiating flow at the other end of the connection. They differ from the [FlowContextProperties] available to all
     * flows via the [FlowEngine] in that they are not a description of the context of the currently executing flow, but
     * instead the flow which initiated it.
     *
     * Any calls to modify these contextProperties will throw a [CordaRuntimeException], they should be considered
     * immutable.
     */
    val contextProperties: FlowContextProperties

    /**
     * Serializes and queues the given [payload] object for sending to the [counterparty]. Suspends until a response is
     * received, which must be of the given [receiveType].
     *
     * Note that this function is not just a simple send and receive pair. It is more efficient and more correct to use
     * sendAndReceive when you expect to do a message swap rather than use [FlowSession.send] and then
     * [FlowSession.receive].
     *
     * @param R The data type received from the counterparty.
     * @param receiveType The data type received from the counterparty.
     * @param payload The data sent to the counterparty.
     *
     * @return The received data [R]
     *
     * @throws [CordaRuntimeException] if the session is closed or in a failed state.
     */
    @Suspendable
    fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R


    /**
     * Suspends until a message of type [R] is received from the [counterparty].
     *
     * @param R The data type received from the counterparty.
     * @param receiveType The data type received from the counterparty.
     *
     * @return The received data [R]
     *
     * @throws [CordaRuntimeException] if the session is closed or in a failed state.
     */
    @Suspendable
    fun <R : Any> receive(receiveType: Class<R>): R


    /**
     * Queues the given [payload] for sending to the [counterparty] and continues without suspending.
     *
     * Note that the other party may receive the message at some arbitrary later point or not at all: if [counterparty]
     * is offline then message delivery will be retried until it comes back or until the message is older than the
     * network's event horizon time.
     *
     * @param payload The data sent to the counterparty.
     *
     * @throws [CordaRuntimeException] if the session is closed or in a failed state.
     */
    @Suspendable
    fun send(payload: Any)

    /**
     * Closes this session and performs cleanup of any resources tied to this session.
     *
     * Note that sessions are closed automatically when the corresponding top-level flow terminates.
     *
     * So, it's beneficial to eagerly close them in long-lived flows that might have many open sessions that are not
     * needed anymore and consume resources (e.g. memory, disk etc.).
     *
     * A closed session cannot be used anymore, e.g. to send or receive messages. So, you have to ensure you are calling
     * this method only when the session is not going to be used anymore. As a result, any operations on a closed
     * session will fail with an [CordaRuntimeException].
     *
     * When a session is closed, the other side is informed and the session is closed there too eventually.
     */
    @Suspendable
    fun close()
}

/**
 * Serializes and queues the given [payload] object for sending to the counterparty. Suspends until a response is
 * received, which must be of the given [R] type.
 *
 * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to use this
 * when you expect to do a message swap than do use [FlowSession.send] and then [FlowSession.receive] in turn.
 *
 * @param R The data type received from the counterparty.
 * @param payload The data sent to the counterparty.
 *
 * @return The received data [R]
 */
@Suspendable
inline fun <reified R : Any> FlowSession.sendAndReceive(payload: Any): R {
    return sendAndReceive(R::class.java, payload)
}

/**
 * Suspends until a message of type [R] is received from the counterparty.
 *
 * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
 * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly corrupted
 * data in order to exploit your code.
 *
 * @param R The data type received from the counterparty.
 *
 * @return The received data [R]
 */
@Suspendable
inline fun <reified R : Any> FlowSession.receive(): R {
    return receive(R::class.java)
}
