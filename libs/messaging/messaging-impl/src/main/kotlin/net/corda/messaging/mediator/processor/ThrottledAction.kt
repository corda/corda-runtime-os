package net.corda.messaging.mediator.processor

import java.time.Duration
import java.util.concurrent.TimeUnit

class ThrottledAction(interval: Duration) {
    companion object {
        val NANOS_IN_MILLIS = TimeUnit.MILLISECONDS.toNanos(1)
    }

    private val intervalNanos = interval.toNanos()

    @Volatile
    private var nextTime = System.nanoTime() + intervalNanos

    fun <T, C : Collection<T>> throttled(task: () -> C): C {
        val now = waitForDuration()
        val collection = task()
        nextTime = now + (collection.size * intervalNanos)
        return collection
    }

    private fun waitForDuration(): Long {
        do {
            val now = System.nanoTime()
            val delayTime = nextTime - now
            if (delayTime <= 0) {
                return now
            }
            Thread.sleep(delayTime / NANOS_IN_MILLIS, (delayTime % NANOS_IN_MILLIS).toInt())
        } while (true)
    }
}