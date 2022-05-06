package net.corda.lifecycle.domino.logic.util

import java.util.concurrent.ExecutorService

class AutoClosableExecutorService(private val service: ExecutorService): AutoCloseable {
    override fun close() {
        service.shutdownNow()
    }
}
