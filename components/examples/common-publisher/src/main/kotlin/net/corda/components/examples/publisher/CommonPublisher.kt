package net.corda.components.examples.publisher

import com.typesafe.config.Config
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class CommonPublisher (
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val publisherFactory: PublisherFactory,
    private val instanceId: Int?,
    private val config: Config
    ) : Lifecycle {

    private var publisher: Publisher? = null

    companion object {
        val log: Logger = contextLogger()
        const val clientId = "CommonPublisher"
    }

    override var isRunning: Boolean = false

    private val coordinator = coordinatorFactory.createCoordinator<CommonPublisher>(::eventHandler)

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("CommonPublisher received: $event")
        when (event) {
            is StartEvent -> {
                publisher?.close()
                publisher = publisherFactory.createPublisher(PublisherConfig(clientId, instanceId), config)
                coordinator.updateStatus(LifecycleStatus.UP, "Publisher created")
            }
            is StopEvent -> {
                publisher?.close()
                isRunning = false
            }
        }
    }

    fun publishRecords(records: List<Record<*, *>>) : List<CompletableFuture<Unit>> {
        return publisher?.publish(records) ?: throw IllegalStateException("Publisher has not been started")
    }
}
