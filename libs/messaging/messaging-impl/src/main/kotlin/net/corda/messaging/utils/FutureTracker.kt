package net.corda.messaging.utils

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.messaging.api.exception.CordaRPCAPISenderException

/**
 * Data structure to keep track of active partitions and futures for a given sender and partition listener pair
 *
 * [futuresInPartitionMap] is a map where the key is the partition number we listen for responses on for the futures we
 * hold in the value part of this map
 */
class FutureTracker<RESPONSE> {

    private val futuresInPartitionMap = ConcurrentHashMap<Int, WeakValueHashMap<String, CompletableFuture<RESPONSE>>>()

    fun addFuture(correlationId: String, future: CompletableFuture<RESPONSE>, partition: Int) {
        if (futuresInPartitionMap[partition] == null) {
            future.completeExceptionally(
                CordaRPCAPISenderException(
                    "Repartition event!! Partition was removed before we could send the request. Please retry"
                )
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

    fun addPartitions(partitions: List<CordaTopicPartition>) {
        for (partition in partitions) {
            futuresInPartitionMap[partition.partition] = WeakValueHashMap()
        }
    }

    fun removePartitions(partitions: List<CordaTopicPartition>) {
        for (partition in partitions) {
            val futures = futuresInPartitionMap[partition.partition]
            for (key in futures!!.keys) {
                futures[key]?.completeExceptionally(
                    CordaRPCAPIPartitionException(
                        "Repartition event!! Results for this future can no longer be returned"
                    )
                )
            }
            futuresInPartitionMap.remove(partition.partition)
        }
    }
}
