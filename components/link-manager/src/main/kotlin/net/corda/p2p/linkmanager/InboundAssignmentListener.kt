package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InboundAssignmentListener(coordinatorFactory: LifecycleCoordinatorFactory): PartitionAssignmentListener, LifecycleWithDominoTile {

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        createResources = ::createResources
    )

    private val lock = ReentrantReadWriteLock()
    private val topicToPartition = mutableMapOf<String, MutableSet<Int>>()
    private var firstAssignment = true
    private val topicToCallback = mutableMapOf<String, (partitions: Set<Int>) -> Unit>()

    private val future: CompletableFuture<Unit> = CompletableFuture()

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            for ((topic, partition) in topicPartitions) {
                topicToPartition[topic]?.remove(partition)
            }
            callCallBacks(topicPartitions.map { it.first }.toSet())
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            for ((topic, partition) in topicPartitions) {
                val partitionSet = topicToPartition.computeIfAbsent(topic) { mutableSetOf() }
                partitionSet.add(partition)
            }
            callCallBacks(topicPartitions.map { it.first }.toSet())
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
            topicToCallback[topic] = callback
            if (future.isDone) {
                callCallBacks(setOf(topic))
            }
        }
    }

    private fun callCallBacks(topics: Set<String>) {
        topics.forEach { topic ->
            val callback = topicToCallback[topic]
            val partitions = topicToPartition[topic]
            if (callback != null && partitions != null) {
                callback(partitions)
            }
        }
    }

    private fun createResources(@Suppress("UNUSED_PARAMETER") resourcesHolder: ResourcesHolder): CompletableFuture<Unit> {
        return future
    }
}
