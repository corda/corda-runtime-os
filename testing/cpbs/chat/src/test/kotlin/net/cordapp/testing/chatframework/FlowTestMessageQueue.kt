package net.cordapp.testing.chatframework

import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A thread safe message queue which links two FlowSessions such that messages send by the 'from' session can be
 * received by the 'to' session. For convenience consider using FlowMockMessageLink instead of this class which
 * wraps up this class with some other useful features.
 *
 * Take note also of FlowTestMessageQueue.addExpectedMessageType<>() which loads this class with expected message
 * types, without calling this method with expected types this class will exhibit unexpected behaviour.
 */
class FlowTestMessageQueue(val from: FlowSession, val to: FlowSession) {
    companion object {
        private val RECEIVE_TIMEOUT_SECONDS = 5L
    }

    private val queue = LinkedList<Any>()
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    init {
        whenever(from.send(any())).then {
            lock.withLock {
                queue.add(it.arguments[0])
                condition.signal()
            }
        }
    }

    fun getOrWaitForNextMessage(): Any {
        lock.withLock {
            while (queue.isEmpty()) {
                condition.await(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            return queue.remove()
        }
    }

    fun failIfNotEmpty() {
        lock.withLock {
            if (!queue.isEmpty()) {
                fail(
                    "Messages were left in the FlowTestMessageQueue. More messages were sent by the 'from' " +
                            "Flow than received by the 'to' Flows."
                )
            }
        }
    }
}

/**
 * Each message type must be registered with FlowTestMessageQueue so that an expectation can be set up for it.
 * If an expectation is not set up for a type and a 'receive' is attempted for that type, the 'receive' will
 * drop through with default Mockito behaviour. In this case your test will exhibit undefined behaviour as that
 * single 'receive' doesn't return the next thing off the queue but the Flow will continue to execute believing
 * something was received.
 */
inline fun <reified T : Any> FlowTestMessageQueue.addExpectedMessageType() {
    whenever(this.to.receive(T::class.java))
        .thenAnswer {
            UntrustworthyData(this.getOrWaitForNextMessage() as T)
        }
}
