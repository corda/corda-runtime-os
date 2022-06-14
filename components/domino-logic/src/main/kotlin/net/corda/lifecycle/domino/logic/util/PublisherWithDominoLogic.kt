package net.corda.lifecycle.domino.logic.util

import java.util.concurrent.CompletableFuture
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class PublisherWithDominoLogic(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherConfig: PublisherConfig,
    messagingConfiguration: SmartConfig,
) : LifecycleWithDominoTile {
    companion object {
        private val instancesIndex = AtomicInteger()
        private const val componentName = "Publisher"
    }

    private val publisher = AtomicReference<Publisher>()
    private val lifecycleLock = ReentrantReadWriteLock()

    override val dominoTile = object: DominoTile {

        override val coordinatorName = LifecycleCoordinatorName(componentName, instancesIndex.getAndAdd(1).toString())
        override val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

        override val dependentChildren: Collection<LifecycleCoordinatorName> = emptySet()
        override val managedChildren: Collection<Lifecycle> = emptySet()

        override fun start() {
            coordinator.start()
        }

        override fun stop() {
            coordinator.postEvent(StopEvent())
        }

        override fun close() {
            coordinator.postEvent(StopEvent())
            coordinator.close()
        }

        private inner class EventHandler : LifecycleEventHandler {
            override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
                when (event) {
                    is StartEvent -> {
                        lifecycleLock.write {
                            if (coordinator.status == LifecycleStatus.DOWN) {
                                val newPublisher = publisherFactory.createPublisher(publisherConfig, messagingConfiguration)
                                newPublisher.start()
                                publisher.set(newPublisher)
                                coordinator.updateStatus(LifecycleStatus.UP)
                            }
                        }
                    }
                    is StopEvent -> {
                        lifecycleLock.write {
                            if (coordinator.status != LifecycleStatus.DOWN) {
                                val oldPublisher = publisher.getAndSet(null)
                                oldPublisher.close()
                                coordinator.updateStatus(LifecycleStatus.DOWN)
                            }
                        }
                    }
                }
            }
        }
    }

    fun <T> withLifecycleLock(access: () -> T): T {
        return lifecycleLock.read {
            access.invoke()
        }
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return publisher.get()?.publishToPartition(records) ?: throw IllegalStateException("Publisher had not started")
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return publisher.get()?.publish(records) ?: throw IllegalStateException("Publisher had not started")
    }
}
