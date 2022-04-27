package net.corda.lifecycle.domino.logic.util

import java.util.concurrent.ScheduledExecutorService

class AutoClosableScheduledExecutorService(private val service: ScheduledExecutorService): AutoCloseable {
    override fun close() {
        service.shutdownNow()
    }
}
