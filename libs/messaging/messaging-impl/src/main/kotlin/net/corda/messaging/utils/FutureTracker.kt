package net.corda.messaging.utils

import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Data structure to keep track of active partitions and futures for a given sender and partition listener pair
 *
 * [futuresInPartitionMap] is a map where the key is the partition number we listen for responses on for the futures we
 * hold in the value part of this map
 */
class FutureTracker<RESPONSE> : AutoCloseable {
    private val futuresInPartitionMap = ConcurrentHashMap<Int, WeakValueHashMap<String, CompletableFuture<RESPONSE>>>()

    fun addFuture(correlationId: String, future: CompletableFuture<RESPONSE>, partition: Int) {
        if (futuresInPartitionMap[partition] == null) {
            future.completeExceptionally(
                CordaRPCAPISenderException("Partition was removed before we could send the request. Please retry.")
            )
        } else {
            futuresInPartitionMap[partition]?.put(correlationId, future)
        }
    }

    fun getFuture(correlationId: String, partition: Int): CompletableFuture<RESPONSE>? {
        return futuresInPartitionMap[partition]?.get(correlationId)
    }

    fun removeFuture(correlationId: String, partition: Int) {
        futuresInPartitionMap[partition]?.remove(correlationId)
    }

    fun addPartition(partition: Int) {
        futuresInPartitionMap[partition] = WeakValueHashMap()
    }

    fun addPartitions(partitions: List<Int>) {
        for (partition in partitions) {
            addPartition(partition)
        }
    }

    private fun removePartition(partition: Int) {
        val futures = futuresInPartitionMap[partition]

        for (key in futures!!.keys) {
            futures[key]?.completeExceptionally(
                CordaRPCAPIPartitionException("Partition was removed, results for this future can no longer be returned.")
            )
        }

        futuresInPartitionMap.remove(partition)
    }

    fun removePartitions(partitions: List<Int>) {
        for (partition in partitions) {
            removePartition(partition)
        }
    }

    override fun close() {
        for (partition in futuresInPartitionMap.keys()) {
            removePartition(partition)
        }
    }
}
