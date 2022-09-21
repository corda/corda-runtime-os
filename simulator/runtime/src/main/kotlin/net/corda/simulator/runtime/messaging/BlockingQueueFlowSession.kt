package net.corda.simulator.runtime.messaging

import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BlockingQueueFlowSession(
    private val flowDetails: FlowContext,
    private val from: BlockingQueue<Any>,
    private val to: BlockingQueue<Any>
) : FlowSession {

    private val configuration = flowDetails.configuration
    private var caughtResponderError: Throwable? = null
    override fun close() {
        TODO("Not yet implemented")
    }

    override val contextProperties: FlowContextProperties
        get() {
            TODO("Not yet implemented")
        }

    override val counterparty: MemberX500Name
        get() = flowDetails.member

    override fun <R : Any> receive(receiveType: Class<R>): R {
        val start = configuration.clock.instant()
        while (configuration.clock.instant().minus(configuration.timeout) < start) {
            val immutableResponderError = caughtResponderError
            if (immutableResponderError != null) {
                throw ResponderFlowException(immutableResponderError)
            }
            val received = to.poll(configuration.pollInterval.toMillis(), TimeUnit.MILLISECONDS)
            if (received != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    return received as R
                } catch (e: ClassCastException) {
                    throw IllegalStateException("Message on queue was not a ${receiveType.simpleName}")
                }
            }
        }
        throw TimeoutException("Session belonging to \"$counterparty\" timed out after ${configuration.timeout}")
    }

    override fun send(payload: Any) {
        from.put(payload)
    }

    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
        send(payload)
        return receive(receiveType)
    }

    fun responderErrorCaught(t: Throwable) {
        caughtResponderError = t
    }
}
