package net.corda.p2p.linkmanager

import java.util.concurrent.ScheduledExecutorService

class AutoClosableScheduledExecutorService(private val service: ScheduledExecutorService): AutoCloseable {
    override fun close() {
        service.shutdown()
    }
}