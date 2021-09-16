package net.corda.cipher.suite.impl.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component(service = [CryptoServiceLifecycleEventHandler::class])
class CryptoServiceLifecycleEventHandler : LifecycleEventHandler {
    companion object {
        private val logger = contextLogger()
    }

    private val handlers = CopyOnWriteArrayList<LifecycleEventHandler>()

    fun add(handler: LifecycleEventHandler) {
        handlers.add(handler)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        handlers.forEach {
            try {
                it.processEvent(event, coordinator)
            } catch (e: Throwable) {
                logger.error("Handler <${it::class.java.name}> Failed to process event <${event::class.java.name}>, moving on...")
            }
        }
    }
}