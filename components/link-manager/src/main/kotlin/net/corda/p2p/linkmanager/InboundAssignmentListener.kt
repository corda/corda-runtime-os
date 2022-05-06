package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class InboundAssignmentListener(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val partitionsTopic: String,
) : PartitionAssignmentListener, LifecycleWithDominoTile {

    override val dominoTile = SimpleDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
    )

    private val partitions = ConcurrentHashMap.newKeySet<Int>()
    private val callbacks = ConcurrentHashMap.newKeySet<(partitions: Set<Int>) -> Unit>()
    private var logger = LoggerFactory.getLogger(this::class.java.name)

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        topicPartitions.forEach { (topic, partition) ->
            if (topic != partitionsTopic) {
                logger.warn("Unexpected topic: $topic unassigned, expected $topicPartitions")
            } else {
                partitions.remove(partition)
            }
        }

        if (partitions.isEmpty()) {
            dominoTile.updateState(DominoTileState.StoppedDueToBadConfig)
        }
        callCallbacks()
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        topicPartitions.forEach { (topic, partition) ->
            if (topic != partitionsTopic) {
                logger.warn("Unexpected topic: $topic assigned, expected $topicPartitions")
            } else {
                partitions.add(partition)
            }
        }
        if (partitions.isNotEmpty()) {
            dominoTile.updateState(DominoTileState.Started)
            callCallbacks()
        }
    }

    fun getCurrentlyAssignedPartitions(): Set<Int> {
        return partitions
    }

    fun registerCallbackForTopic(callback: (partitions: Set<Int>) -> Unit) {
        callbacks.add(callback)
        callCallbacks()
    }

    private fun callCallbacks() {
        if (dominoTile.isRunning) {
            callbacks.forEach {
                it.invoke(partitions)
            }
        }
    }
}
