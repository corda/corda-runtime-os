package net.corda.processors.db.internal.config.writer

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
class ConfigWriteEventHandler(private val configWriterSubscriptionFactory: ConfigWriterSubscriptionFactory) :
    LifecycleEventHandler {

    // A handle for stopping the processing of new config requests.
    private var subscriptionHandle: Lifecycle? = null

    /**
     * Upon [SubscribeEvent], starts processing new config requests. Upon [StopEvent], stops processing them.
     *
     * @throws ConfigWriteException If any [SubscribeEvent]s are received beyond the first, or if an event type other
     * than [StartEvent]/[SubscribeEvent]/[StopEvent] is received.
     * TODO - Joel - Document what else is thrown.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required bootstrap config.

            // TODO - Joel - Handle repeated subscription attempts? If so, differentiate between duplicate subscribes
            //  with the same vs different config?
            is SubscribeEvent -> {
                if (subscriptionHandle != null) {
                    throw ConfigWriteException("An attempt was made to subscribe twice.")
                }

                // TODO - Joel - Catch errors and set unhealthy status.
                // TODO - Joel - Check if DB can be connected to, at a minimum.

                subscriptionHandle = configWriterSubscriptionFactory.create(event.config, event.instanceId)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                subscriptionHandle?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}