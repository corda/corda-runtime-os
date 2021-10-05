package net.corda.p2p.gateway.domino.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.p2p.gateway.domino.LeafTile
import java.util.concurrent.CompletableFuture

class PublisherWithDominoLogic(
    private val publisher: Publisher,
    coordinatorFactory: LifecycleCoordinatorFactory
) :
    LeafTile(coordinatorFactory) {
    override fun createResources() {
        publisher.start()
        executeBeforeStop {
            publisher.close()
        }
        updateState(State.Started)
    }

    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return publisher.publishToPartition(records)
    }

    fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return publisher.publish(records)
    }
}
