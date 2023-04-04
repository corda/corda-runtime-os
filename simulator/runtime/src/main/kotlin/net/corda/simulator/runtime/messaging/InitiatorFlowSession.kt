package net.corda.simulator.runtime.messaging

import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import org.slf4j.LoggerFactory

interface InitiatorFlowSession : FlowSession {
    /**
     * Used by [ConcurrentFlowMessaging] to indicate that an exception has been detected on
     * the responding thread.
     *
     * @param t The error thrown by the responding thread.
     */
    fun responderErrorCaught(t: Throwable)
    fun responderClosed()
}

class BaseInitiatorFlowSession(
    private val flowDetails: FlowContext,
    from: BlockingQueue<Any>,
    to: BlockingQueue<Any>,
    flowContextProperties: SimFlowContextProperties
) : BlockingQueueFlowSession(flowDetails, from, to, flowContextProperties), InitiatorFlowSession {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var responderSessionClosed: Boolean = false
    private val runningLock = ReentrantLock()
    private val runningCondition = runningLock.newCondition()

    /**
     * @param payload The payload to send to the counterparty.
     */
    override fun send(payload: Any) {
        rethrowAnyResponderError()
        state.closedCheck()
        to.put(payload)
    }

    override fun rethrowAnyResponderError() {
        val immutableResponderError = counterpartyError
        if (immutableResponderError != null) {
            throw ResponderFlowException(immutableResponderError)
        }
    }

    /**
     * Used by [net.corda.v5.application.messaging.FlowMessaging] to indicate that an exception has been detected on
     * the responding thread.
     *
     * @param t The error thrown by the responding thread.
     */
    override fun responderErrorCaught(t: Throwable) {
        counterpartyError = t
        runningLock.withLock {
            runningCondition.signalAll()
        }
    }

    override fun responderClosed() {
        responderSessionClosed = true
        log.info("Initiator session with protocol ${flowDetails.protocol}, " +
                "counterparty \"${flowDetails.member}\"; responder signalled close")
        runningLock.withLock {
            runningCondition.signalAll()
        }
    }

    override fun getCounterpartyFlowInfo(): FlowInfo {
        TODO("Not yet implemented")
    }

    override fun close() {
        rethrowAnyResponderError()
        log.info("Closing initiator session with protocol ${flowDetails.protocol}, " +
                "counterparty \"${flowDetails.member}\"; responder is closed: $responderSessionClosed")

        val start = configuration.clock.instant()
        while(
            !responderSessionClosed
            && counterpartyError == null
        ) {
            checkTimeout(start)
            runningLock.withLock {
                println("Waiting for responder to close")
                runningCondition.await(configuration.pollInterval.toMillis(), TimeUnit.MILLISECONDS)
            }
        }
        rethrowAnyResponderError()
        state = SessionState.CLOSED
        log.info("Closed initiator session with protocol ${flowDetails.protocol}, " +
                "counterparty \"${flowDetails.member}\"")
    }
}