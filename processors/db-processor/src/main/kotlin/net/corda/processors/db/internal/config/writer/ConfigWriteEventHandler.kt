package net.corda.processors.db.internal.config.writer

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import java.io.Closeable

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
class ConfigWriteEventHandler(private val configWriterSubscriptionFactory: ConfigWriterSubscriptionFactory) :
    LifecycleEventHandler {

    // A handle for stopping the processing of new config requests.
    private var subscriptionHandle: Closeable? = null

    /**
     * Upon [SubscribeEvent], starts processing new config requests. Upon [StopEvent], stops processing them.
     *
     * @throws ConfigWriteException If any [SubscribeEvent]s are received beyond the first, if an event type other
     * than [StartEvent]/[SubscribeEvent]/[StopEvent] is received, or if the cluster database cannot be connected to.
     * TODO - Joel - Document what else is thrown.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required bootstrap config.

            // TODO - Joel - Catch errors here and set unhealthy status.
            is SubscribeEvent -> {
                if (subscriptionHandle != null) {
                    throw ConfigWriteException("An attempt was made to subscribe twice.")
                }

                val dbUtils = event.dbUtils
                dbUtils.checkClusterDatabaseConnection()
                dbUtils.migrateClusterDatabase()

                subscriptionHandle = configWriterSubscriptionFactory.create(event.config, event.instanceId, dbUtils)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                subscriptionHandle?.close()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}