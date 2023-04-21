package net.corda.lifecycle.domino.logic.util

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque

class ResourcesHolder : AutoCloseable {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private val resources = ConcurrentLinkedDeque<AutoCloseable>()

    fun keep(resource: AutoCloseable) {
        resources.addFirst(resource)
    }
    override fun close() {
        while (resources.isNotEmpty()) {
            val resource = resources.pollFirst()
            try {
                resource.close()
            } catch (e: Throwable) {
                logger.warn("Fail to close", e)
            }
        }
    }
}
