package net.corda.simulator.runtime.messaging

import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.exceptions.SessionAlreadyClosedException
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class SessionState {
    AVAILABLE { override fun closedCheck() {} },
    CLOSED { override fun closedCheck() { throw SessionAlreadyClosedException() } };

    abstract fun closedCheck()
}


/**
 * Implementation of [FlowSession] that uses two [BlockingQueue]s to enable sender and receiver to communicate.
 *
 * @param flowDetails The context in which this session is taking place.
 * @param from The queue on which to receive.
 * @param to The queue on which to send.
 * @param flowContextProperties The [FlowContextProperties] for the session
 */
abstract class BlockingQueueFlowSession(
    private val flowDetails: FlowContext,
    protected val from: BlockingQueue<Any>,
    protected val to: BlockingQueue<Any>,
    protected val flowContextProperties: SimFlowContextProperties
) : FlowSession {


    protected val configuration = flowDetails.configuration
    protected var counterpartyError: Throwable? = null
    protected abstract fun rethrowAnyResponderError()

    protected var state = SessionState.AVAILABLE

    /**
     * Not implemented.
     */
    override fun getContextProperties(): FlowContextProperties
        = flowContextProperties.toImmutableContext()

    /**
     * Returns the counterparty with whom this session has been opened.
     */
    override fun getCounterparty(): MemberX500Name
        = flowDetails.member

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
        state.closedCheck()
        requireBoxedType(receiveType)
        val start = configuration.clock.instant()
        while (true) {
            checkTimeout(start)
            rethrowAnyResponderError()
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
    }

    protected fun checkTimeout(start: Instant) {
        val now = configuration.clock.instant()
        val against = start.plus(configuration.timeout)
        val timedOut = now > against
        if(timedOut) {
            throw TimeoutException("Session belonging to \"$counterparty\" timed out after ${configuration.timeout}")
        }
    }

    private fun requireBoxedType(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    /**
     * @param receiveType The class of the received payload.
     * @param payload The payload to send.
     *
     * @return The received payload.
     * @see [BlockingQueueFlowSession.receive] for details of thrown exceptions.
     */
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
        requireBoxedType(receiveType)
        send(payload)
        return receive(receiveType)
    }
}