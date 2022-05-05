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

class InboundAssignmentListener(coordinatorFactory: LifecycleCoordinatorFactory) : PartitionAssignmentListener, LifecycleWithDominoTile {

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        createResources = ::createResources,
        continuesResourceStarter = true,
    )

    private val lock = ReentrantReadWriteLock()
    private val topicToPartition = mutableMapOf<String, MutableSet<Int>>()
    private var needToComplete = true
    private val topicToCallback = mutableMapOf<String, MutableList<(partitions: Set<Int>) -> Unit>>()
    private var future = CompletableFuture<Unit>()

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            topicPartitions.forEach { (topic, partition) ->
                topicToPartition.computeIfPresent(topic) { _, knownPartitions ->
                    knownPartitions.remove(partition)
                    if (knownPartitions.isEmpty()) {
                        null
                    } else {
                        knownPartitions
                    }
                }
            }
            if (topicToPartition.isEmpty()) {
                needToComplete = true
                val oldFuture = future
                future = CompletableFuture()
                oldFuture.completeExceptionally(NoPartitionAssigned())
            }
            callCallbacks(topicPartitions.map { it.first }.toSet())
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            for ((topic, partition) in topicPartitions) {
                topicToPartition.computeIfAbsent(topic) {
                    mutableSetOf()
                }.add(partition)
            }
            callCallbacks(topicPartitions.map { it.first }.toSet())
            if ((needToComplete) && (topicToPartition.isNotEmpty())) {
                needToComplete = false
                val oldFuture = future
                future = CompletableFuture()
                oldFuture.complete(Unit)
            }
        }
    }

    fun getCurrentlyAssignedPartitions(topic: String): Set<Int> {
        return lock.read {
            topicToPartition[topic] ?: emptySet()
        }
    }

    fun registerCallbackForTopic(topic: String, callback: (partitions: Set<Int>) -> Unit) {
        lock.write {
            topicToCallback.compute(topic) { _, callbacks ->
                callbacks?.apply { add(callback) } ?: mutableListOf(callback)
            }
            if ((!needToComplete) && (topicToCallback.isNotEmpty())) {
                callCallbacks(setOf(topic))
            }
        }
    }

    private fun callCallbacks(topics: Set<String>) {
        topics.forEach { topic ->
            val callbacks = topicToCallback[topic]
            val partitions = topicToPartition[topic]
            partitions?.let { callbacks?.forEach { callback -> callback(partitions) } }
        }
    }

    private fun createResources(@Suppress("UNUSED_PARAMETER") resourcesHolder: ResourcesHolder): CompletableFuture<Unit> {
        return lock.read {
            future
        }
    }

    internal class NoPartitionAssigned : Exception("No partition assign")
}
