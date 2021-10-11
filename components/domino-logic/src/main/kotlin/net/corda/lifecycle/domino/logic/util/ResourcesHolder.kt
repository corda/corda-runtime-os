package net.corda.lifecycle.domino.logic.util

import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentLinkedDeque

class ResourcesHolder : AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }
    private val resources = ConcurrentLinkedDeque<AutoCloseable>()

    fun keep(resource: AutoCloseable) {
        resources.addFirst(resource)
    }
    override fun close() {
        do {
            val resource = resources.pollFirst()
            if (resource != null) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    resource.close()
                } catch (e: Throwable) {
                    logger.warn("Fail to close", e)
                }
            } else {
                break
            }
        } while (true)
    }
}
