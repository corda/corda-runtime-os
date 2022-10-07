package net.corda.simulator.runtime.messaging

import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Implementation of [FlowSession] that uses two [BlockingQueue]s to enable sender and receiver to communicate.
 *
 * @param flowDetails The context in which this session is taking place.
 * @param from The queue on which to receive.
 * @param to The queue on which to send.
 */
class BlockingQueueFlowSession(
    private val flowDetails: FlowContext,
    private val from: BlockingQueue<Any>,
    private val to: BlockingQueue<Any>
) : FlowSession {

    private val configuration = flowDetails.configuration
    private var caughtResponderError: Throwable? = null

    /**
     * Not implemented.
     */
    override fun close() {
        TODO("Not yet implemented")
    }

    /**
     * Not implemented.
     */
    override val contextProperties: FlowContextProperties
        get() {
            TODO("Not yet implemented")
        }

    /**
     * Returns the counterparty with whom this session has been opened.
     */
    override val counterparty: MemberX500Name
        get() = flowDetails.member

    /**
     * Waits to receive a payload from the counterparty, polling to check for any detected errors on the
     * counterparty flow.
     *
     * @param receiveType The class of the received payload.
     * @return The received message.
     *
     * @throws TimeoutException if no payload is received before the configured timeout.
     * @throws ClassCastException if the received payload could not be cast to the declared type.
     * @throws ResponderFlowException if an error was detected in the responding flow.
     */
    override fun <R : Any> receive(receiveType: Class<R>): R {
        val start = configuration.clock.instant()
        while (configuration.clock.instant().minus(configuration.timeout) < start) {
            val immutableResponderError = caughtResponderError
            if (immutableResponderError != null) {
                throw ResponderFlowException(immutableResponderError)
            }
            val received = from.poll(configuration.pollInterval.toMillis(), TimeUnit.MILLISECONDS)
            if (received != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    return received as R
                } catch (e: ClassCastException) {
                    throw IllegalStateException("Message on queue was not a ${receiveType.simpleName}", e)
                }
            }
        }
        throw TimeoutException("Session belonging to \"$counterparty\" timed out after ${configuration.timeout}")
    }

    /**
     * @param payload The payload to send to the counterparty.
     */
    override fun send(payload: Any) {
        to.put(payload)
    }

    /**
     * @param receiveType The class of the received payload.
     * @param payload The payload to send.
     *
     * @return The received payload.
     * @see [BlockingQueueFlowSession.receive] for details of thrown exceptions.
     */
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
        send(payload)
        return receive(receiveType)
    }

    /**
     * Used by [net.corda.v5.application.messaging.FlowMessaging] to indicate that an exception has been detected on
     * the responding thread.
     *
     * @param t The error thrown by the responding thread.
     */
    fun responderErrorCaught(t: Throwable) {
        caughtResponderError = t
    }
}
