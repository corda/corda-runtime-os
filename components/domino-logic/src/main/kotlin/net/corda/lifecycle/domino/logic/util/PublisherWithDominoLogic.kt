package net.corda.lifecycle.domino.logic.util

import com.typesafe.config.ConfigFactory
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
) :
    LeafTile(coordinatorFactory) {

    @Volatile
    private var publisher: Publisher? = null
    override fun createResources() {
        publisher?.close()
        val publisherConfig = PublisherConfig(publisherId)
        publisher = publisherFactory.createPublisher(publisherConfig, ConfigFactory.empty()).also {
            resources.keep(it)
            it.start()
        }

        started()
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return dataAccess {
            val publisher = publisher
            if (publisher != null) {
                publisher.publishToPartition(records)
            } else {
                throw IllegalStateException("Publisher had not started")
            }
        }
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return dataAccess {
            val publisher = publisher
            if (publisher != null) {
                publisher.publish(records)
            } else {
                throw IllegalStateException("Publisher had not started")
            }
        }
    }
}
