package net.corda.configuration.write.impl.publish

import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory

class ConfigPublishServiceHandler(
    private val publisherFactory: PublisherFactory,
    private val configMerger: ConfigMerger
) : LifecycleEventHandler {

    companion object {
        private const val CONFIG_PUBLISH_GROUP = "config.publisher"
        private const val CONFIG_PUBLISH_CLIENT = "$CONFIG_PUBLISH_GROUP.client"
    }

    internal var publisher: Publisher? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when(event) {
            is StopEvent -> onStopEvent()
            is BootstrapConfigEvent -> onBootstrapConfigEvent(event, coordinator)
        }
    }

    private fun onStopEvent() {
        publisher?.close()
        publisher = null
    }

    private fun onBootstrapConfigEvent(event: BootstrapConfigEvent, coordinator: LifecycleCoordinator) {
        val bootstrapConfig = event.bootstrapConfig
        val messagingConfig = configMerger.getMessagingConfig(bootstrapConfig)
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig(CONFIG_PUBLISH_CLIENT),
            messagingConfig
        ).also { it.start() }
        coordinator.updateStatus(LifecycleStatus.UP)
    }
}