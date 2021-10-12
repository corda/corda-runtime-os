package net.corda.lifecycle.domino.logic.util

import com.typesafe.config.ConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LeafTile
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class PublisherWithDominoLogic(
    private val publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val publisherId: String,
) :
    LeafTile(coordinatorFactory) {
    private val publisher = AtomicReference<Publisher>(null)
    override fun createResources() {
        val publisherConfig = PublisherConfig(publisherId)
        publisherFactory.createPublisher(publisherConfig, ConfigFactory.empty()).also {
            publisher.getAndSet(it)?.close()
            resources.keep(it)
            it.start()
            started()
        }
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        val publisher = publisher.get()
        return if (publisher != null) {
            publisher.publishToPartition(records)
        } else {
            throw IllegalStateException("Publisher had not started")
        }
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        val publisher = publisher.get()
        return if (publisher != null) {
            publisher.publish(records)
        } else {
            throw IllegalStateException("Publisher had not started")
        }
    }
}
