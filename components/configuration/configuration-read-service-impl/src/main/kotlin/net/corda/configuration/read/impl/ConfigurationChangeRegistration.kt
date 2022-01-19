package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicBoolean

class ConfigurationChangeRegistration(
    private val coordinator: LifecycleCoordinator,
    private val handler: ConfigurationHandler
) : AutoCloseable {

    private companion object {
        private val logger = contextLogger()
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