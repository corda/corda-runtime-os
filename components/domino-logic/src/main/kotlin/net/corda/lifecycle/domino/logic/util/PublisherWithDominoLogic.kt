package net.corda.lifecycle.domino.logic.util

import java.util.concurrent.CompletableFuture
import net.corda.libs.configuration.SmartConfig
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
import net.corda.lifecycle.domino.logic.NamedLifecycle
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
): PublisherWithDominoLogicBase<Publisher> (coordinatorFactory, {
    val newPublisher = publisherFactory.createPublisher(publisherConfig, messagingConfiguration)
    newPublisher.start()
    newPublisher
}) {
    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return lifecycleLock.read {
            publisher.get()?.publishToPartition(records) ?: throw IllegalStateException("Publisher had not started")
        }
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return lifecycleLock.read {
            publisher.get()?.publish(records) ?: throw IllegalStateException("Publisher had not started")
        }
    }
}
