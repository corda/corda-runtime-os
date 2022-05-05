package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PartitionObserverDominoTile
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InboundAssignmentListener(coordinatorFactory: LifecycleCoordinatorFactory): PartitionAssignmentListener, LifecycleWithDominoTile {

    override val dominoTile = PartitionObserverDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
    )

    private val lock = ReentrantReadWriteLock()
    private val topicToPartition = mutableMapOf<String, MutableSet<Int>>()
    private var firstAssignment = true
    private val topicToCallback = mutableMapOf<String, MutableList<(partitions: Set<Int>) -> Unit>>()

    private val future: CompletableFuture<Unit> = CompletableFuture()

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            for ((topic, partition) in topicPartitions) {
                topicToPartition[topic]?.remove(partition)
            }
            callCallbacks(topicPartitions.map { it.first }.toSet())
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            for ((topic, partition) in topicPartitions) {
                val partitionSet = topicToPartition.computeIfAbsent(topic) { mutableSetOf() }
                partitionSet.add(partition)
            }
            callCallbacks(topicPartitions.map { it.first }.toSet())
            if (firstAssignment) {
                firstAssignment = false
                future.complete(Unit)
            }
        }
    }

    fun getCurrentlyAssignedPartitions(topic: String) : Set<Int> {
        return lock.read {
            topicToPartition[topic] ?: emptySet()
        }
    }

    fun registerCallbackForTopic(topic: String, callback: (partitions: Set<Int>) -> Unit) {
        lock.write {
            topicToCallback.compute(topic) { _, callbacks ->
                callbacks?.apply { add(callback) } ?: mutableListOf(callback)
            }
            if (future.isDone) {
                callCallbacks(setOf(topic))
            }
        }
    }

    private fun callCallbacks(topics: Set<String>) {
        topics.forEach { topic ->
            val callbacks = topicToCallback[topic]
            val partitions = topicToPartition[topic]
            partitions?.let { callbacks?.forEach { callback -> callback(partitions) } }
            // need to enforce single topic in this class and then we can invoke only for this one here.
            partitions?.let { dominoTile.partitionAllocationChanged(it.toList()) }
        }
    }
}
