package net.corda.processors.db.internal.config.writer

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

class ConfigWriteServiceEventHandler(private val configWriterSubscriptionFactory: ConfigWriterSubscriptionFactory) :
    LifecycleEventHandler {

    private var subscription: Lifecycle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required config.

            is SubscribeEvent -> {
                if (subscription != null) throw ConfigWriteServiceException("TODO - Joel - Exception message.")
                subscription = configWriterSubscriptionFactory.create(event.config, event.instanceId)
                // TODO - Joel - Should I spin here while waiting for DB to come up, if it's not up?
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                subscription?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}