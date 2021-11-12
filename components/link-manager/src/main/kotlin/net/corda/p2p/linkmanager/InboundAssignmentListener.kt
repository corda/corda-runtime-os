package net.corda.p2p.linkmanager

import net.corda.messaging.api.subscription.PartitionAssignmentListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InboundAssignmentListener(private val future: CompletableFuture<Unit>): PartitionAssignmentListener {

    private val lock = ReentrantReadWriteLock()
    private val topicToPartition = mutableMapOf<String, MutableSet<Int>>()
    private var firstAssignment = true

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            for ((topic, partition) in topicPartitions) {
                topicToPartition[topic]?.remove(partition)
            }
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            if (firstAssignment) {
                firstAssignment = false
                future.complete(null)
            }
            for ((topic, partition) in topicPartitions) {
                val partitionSet = topicToPartition.computeIfAbsent(topic) { mutableSetOf() }
                partitionSet.add(partition)
            }
        }
    }

    fun getCurrentlyAssignedPartitions(topic: String) : Set<Int> {
        return lock.read {
            topicToPartition[topic] ?: emptySet()
        }
    }
}