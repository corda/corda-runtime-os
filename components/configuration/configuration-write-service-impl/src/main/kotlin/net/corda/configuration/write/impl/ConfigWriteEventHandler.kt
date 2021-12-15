package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
class ConfigWriteEventHandler(private val configWriterFactory: PersistentConfigWriterFactory) : LifecycleEventHandler {
    private var configWriter: PersistentConfigWriter? = null

    /**
     * Upon [StartProcessingEvent], starts processing cluster configuration updates. Upon [StopEvent], stops processing
     * them.
     *
     * @throws ConfigWriteServiceException If an event type other than [StartEvent]/[StartProcessingEvent]/[StopEvent]
     * is received, if multiple [StartProcessingEvent]s are received, or if the creation of the subscription fails.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start processing updates until we have the required service config.

            is StartProcessingEvent -> {
                if (configWriter != null) {
                    throw ConfigWriteServiceException("An attempt was made to subscribe twice.")
                }

                try {
                    configWriter = configWriterFactory.create(event.config, event.instanceId).apply { start() }
                    coordinator.updateStatus(LifecycleStatus.UP)
                } catch (e: Exception) {
                    coordinator.updateStatus(LifecycleStatus.ERROR)
                    throw ConfigWriteServiceException("Subscribing to config management requests failed. Cause: $e.")
                }
            }

            is StopEvent -> {
                configWriter?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}