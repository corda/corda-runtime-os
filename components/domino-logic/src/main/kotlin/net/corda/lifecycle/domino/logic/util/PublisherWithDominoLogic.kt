package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.DominoTileV2
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTileV2
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture

class PublisherWithDominoLogic(
    private val publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val publisherConfig: PublisherConfig,
    private val configuration: SmartConfig,
) : LifecycleWithDominoTileV2 {

    @Volatile
    private var publisher: Publisher? = null

    override val dominoTile = DominoTileV2(this::class.java.simpleName, coordinatorFactory, ::createResources)

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val resourceReady = CompletableFuture<Unit>()
        publisher = publisherFactory.createPublisher(
            publisherConfig,
            configuration
        ).also {
            resources.keep {
                it.close()
                publisher = null
            }
            it.start()
        }
        resourceReady.complete(Unit)
        return resourceReady
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return dominoTile.withLifecycleLock {
            publisher?.publishToPartition(records) ?: throw IllegalStateException("Publisher had not started")
        }
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return dominoTile.withLifecycleLock {
            publisher?.publish(records) ?: throw IllegalStateException("Publisher had not started")
        }
    }
}
