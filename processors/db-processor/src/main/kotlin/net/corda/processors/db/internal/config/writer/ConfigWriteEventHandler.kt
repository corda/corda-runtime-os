package net.corda.processors.db.internal.config.writer

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.io.Closeable

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
class ConfigWriteEventHandler(private val configWriterSubscriptionFactory: ConfigWriterSubscriptionFactory) :
    LifecycleEventHandler {

    private companion object {
        val logger = contextLogger()
    }

    // A handle for stopping the processing of new config requests.
    private var subscriptionHandle: Closeable? = null

    /**
     * Upon [SubscribeEvent], starts processing new config requests. Upon [StopEvent], stops processing them.
     *
     * @throws ConfigWriteException If an event type other than [StartEvent]/[SubscribeEvent]/[StopEvent] is received,
     * if multiple [SubscribeEvent]s are received, or if the creation of the subscription fails.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required bootstrap config.

            // TODO - Joel - Errors that happen in the processor should also be reflected in the coordinator status.
            is SubscribeEvent -> {
                if (subscriptionHandle != null) {
                    throw ConfigWriteException("An attempt was made to subscribe twice.")
                }

                try {
                    event.dbUtils.checkClusterDatabaseConnection()
                    event.dbUtils.migrateClusterDatabase()

                    subscriptionHandle =
                        configWriterSubscriptionFactory.create(event.config, event.instanceId, event.dbUtils)
                    coordinator.updateStatus(LifecycleStatus.UP)
                } catch (e: Exception) {
                    logger.debug("Subscribing to config management requests failed. Cause: $e.")
                    coordinator.updateStatus(LifecycleStatus.ERROR)
                }
            }

            is StopEvent -> {
                subscriptionHandle?.close()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}