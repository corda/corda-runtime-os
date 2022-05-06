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
    private val observedTopic: String,
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
            if (topic != observedTopic) {
                logger.warn(
                    "Unexpected notifications: notifications for $topic were received, " +
                        "but expected notifications only for $observedTopic"
                )
            } else {
                partitions.remove(partition)
            }
        }

        if (partitions.isEmpty()) {
            logger.warn("No partitions assigned to $observedTopic.")
            dominoTile.updateState(DominoTileState.StoppedDueToBadConfig)
        }
        callCallbacks()
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        topicPartitions.forEach { (topic, partition) ->
            if (topic != observedTopic) {
                logger.warn(
                    "Unexpected notifications: notifications for $topic were received," +
                        " but expected notifications only for $observedTopic"
                )
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
        if (dominoTile.isRunning) {
            callback.invoke(partitions)
        }
    }

    private fun callCallbacks() {
        if (dominoTile.isRunning) {
            callbacks.forEach {
                it.invoke(partitions)
            }
        }
    }
}
