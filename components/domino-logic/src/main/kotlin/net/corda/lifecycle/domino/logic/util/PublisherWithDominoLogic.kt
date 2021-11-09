package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LeafTile
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture

class PublisherWithDominoLogic(
    private val publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val publisherId: String,
    private val nodeConfiguration: SmartConfig,
) :
    LeafTile(coordinatorFactory) {

    @Volatile
    private var publisher: Publisher? = null

    override fun createResources() {
        val publisherConfig = PublisherConfig(publisherId)
        publisher = publisherFactory.createPublisher(
            publisherConfig,
            nodeConfiguration
        ).also {
            resources.keep {
                it.close()
                publisher = null
            }
            it.start()
        }

        started()
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return withLifecycleLock {
            publisher?.publishToPartition(records) ?: throw IllegalStateException("Publisher had not started")
        }
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return withLifecycleLock {
            publisher?.publish(records) ?: throw IllegalStateException("Publisher had not started")
        }
    }
}
