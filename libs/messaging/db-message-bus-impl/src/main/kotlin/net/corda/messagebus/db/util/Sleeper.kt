package net.corda.messagebus.db.util

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object Sleeper {
    private val lock = ReentrantLock()
    private val conditions = ConcurrentHashMap<String, Condition>()

    fun sleep(timeout: Duration, topic: String) {
        val condition = conditions.computeIfAbsent(topic) {
            lock.newCondition()
        }
        lock.withLock {
            condition.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    fun wakeUp(topic: String) {
        conditions[topic]?.also { condition ->
            lock.withLock {
                condition.signalAll()
            }
        }
    }
}
