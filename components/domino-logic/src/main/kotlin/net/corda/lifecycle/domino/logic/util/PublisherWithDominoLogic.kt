package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.Lifecycle
import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture

class PublisherWithDominoLogic(
    private val publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val publisherId: String,
    private val nodeConfiguration: Config,) : Lifecycle {

    override val isRunning: Boolean
        get() = dominoTile.isRunning


    @Volatile
    private var publisher: Publisher? = null

    val dominoTile = DominoTile(this::class.java.simpleName, coordinatorFactory, ::createResources)

    private fun createResources(resources: ResourcesHolder) {
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
        dominoTile.resourcesStarted(false)
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

    override fun start() {
        if (!isRunning) {
            dominoTile.start()
        }
    }

    override fun stop() {
        if (isRunning) {
            dominoTile.stop()
        }
    }
}
