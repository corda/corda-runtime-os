@file:JvmName("FlowSessionUtils")
package net.corda.v5.application.messaging

import net.corda.v5.base.annotations.Suspendable

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
