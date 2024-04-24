package net.corda.messaging.mediator.processor

import java.time.Duration

class PeriodicAction(val interval: Duration) {
    @Volatile
    private var lastTime = System.nanoTime()

    fun periodically(task: () -> Unit) {
        val now = System.nanoTime()
        if (now - lastTime >= interval.toNanos()) {
            lastTime = now
            task()
        }
    }
}