package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.writer.ConfigWriter
import net.corda.configuration.write.impl.writer.ConfigWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
internal class ConfigWriteEventHandler(
    private val configWriterFactory: ConfigWriterFactory
) : LifecycleEventHandler {
    private var configWriter: ConfigWriter? = null

    /**
     * Upon [StartProcessingEvent], starts processing cluster configuration updates. Upon [StopEvent], stops processing
     * them.
     *
     * @throws ConfigWriteServiceException If multiple [StartProcessingEvent]s are received, or if the creation of the
     *  subscription fails.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartProcessingEvent -> {
                if (configWriter != null) {
                    throw ConfigWriteServiceException("An attempt was made to start processing twice.")
                }

                try {
                    // TODO - CORE-3316 - At worker start-up, read back configuration from database and check it
                    //  against Kafka topic.
                    configWriter = configWriterFactory
                        .create(event.config, event.instanceId, event.entityManagerFactory)
                        .apply { start() }
                } catch (e: Exception) {
                    coordinator.updateStatus(ERROR)
                    throw ConfigWriteServiceException("Could not subscribe to config management requests.", e)
                }

                coordinator.updateStatus(UP)
            }

            is StopEvent -> {
                configWriter?.stop()
                configWriter = null
                coordinator.updateStatus(DOWN)
            }
        }
    }
}