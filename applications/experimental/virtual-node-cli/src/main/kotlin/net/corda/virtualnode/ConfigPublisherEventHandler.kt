package net.corda.virtualnode

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/**
 * Simple event handler that just moves through a set of events to publish a config
 * to Kafka for any sub-components to successful use:
 *
 * * connect to kafka
 * * create topics if needed
 * * publish configuration to kafka
 * * start components
 * * signal 'readiness' to the parent
 *
 */
class ConfigPublisherEventHandler(
    private val configPublisher: ConfigPublisher
) : LifecycleEventHandler {
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is TopicsCreated -> onTopicsCreated(coordinator)
            is ConfigPublished -> onConfigPublished(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configPublisher.createTopics()
        coordinator.postEvent(TopicsCreated())
    }

    private fun onTopicsCreated(coordinator: LifecycleCoordinator) {
        configPublisher.publishConfig()
        coordinator.postEvent(ConfigPublished())
    }

    private fun onConfigPublished(coordinator: LifecycleCoordinator) {
        configPublisher.configPublished()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        // All coordinators we've registered against are all now up.
        if (event.status == LifecycleStatus.UP) {
            configPublisher.ready()
        }
    }

    private fun onStopEvent() {
        configPublisher.done()
    }

    private class TopicsCreated : LifecycleEvent
    private class ConfigPublished : LifecycleEvent
}
