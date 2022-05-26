package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture

class PublisherWithDominoLogic(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    registry: LifecycleRegistry,
    publisherConfig: PublisherConfig,
    configuration: SmartConfig,
) : LifecycleWithDominoTile {

    private val publisher = publisherFactory.createPublisher(
        publisherConfig,
        configuration
    )

    override val dominoTile = ComplexDominoTile(
        PublisherWithDominoLogic::class.java.simpleName,
        coordinatorFactory,
        registry,
        onStart = { publisher.start() },
        onClose = { publisher.close() }
    )

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return dominoTile.withLifecycleLock {
            if (dominoTile.isRunning) {
                publisher.publishToPartition(records)
            } else {
                throw IllegalStateException("Publisher had not started")
            }
        }
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return dominoTile.withLifecycleLock {
            if (dominoTile.isRunning) {
                publisher.publish(records)
            } else {
                throw IllegalStateException("Publisher had not started")
            }
        }
    }
}
