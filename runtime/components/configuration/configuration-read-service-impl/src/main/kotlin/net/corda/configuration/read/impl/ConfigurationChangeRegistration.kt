package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.Resource
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class ConfigurationChangeRegistration(
    private val coordinator: LifecycleCoordinator,
    private val handler: ConfigurationHandler
) : Resource {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val isClosed = AtomicBoolean(false)

    fun invoke(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (!isClosed.get()) {
            try {
                handler.onNewConfiguration(changedKeys, config)
            } catch (e: Throwable) {
                logger.warn(
                    "An exception was thrown while attempting to handle a configuration change: ${e.message}",
                    e
                )
            }
        }
    }

    override fun close() {
        val closed = isClosed.getAndSet(true)
        if (!closed) {
            coordinator.postEvent(ConfigRegistrationRemove(this))
        }
    }
}
