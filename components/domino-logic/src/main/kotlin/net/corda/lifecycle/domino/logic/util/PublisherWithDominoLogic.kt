package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture

class PublisherWithDominoLogic(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherConfig: PublisherConfig,
    configuration: SmartConfig,
) : LifecycleWithDominoTile {

    private val publisher = publisherFactory.createPublisher(
        publisherConfig,
        configuration
    ).also {
        it.start()
    }

    override val dominoTile = object: SimpleDominoTile(PublisherWithDominoLogic::class.java.simpleName, coordinatorFactory) {
        override fun start() {
            super.start()
            updateState(DominoTileState.Started)
        }

        override fun close() {
            publisher.close()
            super.close()
        }
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return publisher.publishToPartition(records)
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return publisher.publish(records)
    }
}
