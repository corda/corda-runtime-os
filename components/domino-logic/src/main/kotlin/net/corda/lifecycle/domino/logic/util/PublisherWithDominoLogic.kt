package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.read

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
