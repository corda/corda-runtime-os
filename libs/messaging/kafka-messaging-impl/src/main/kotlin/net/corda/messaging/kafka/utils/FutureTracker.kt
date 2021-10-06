package net.corda.messaging.kafka.utils

import net.corda.messaging.api.exception.CordaRPCAPISenderException
import org.apache.kafka.common.TopicPartition
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class FutureTracker<TRESP> {

    private var futuresInPartitionMap = ConcurrentHashMap<Int, WeakValueHashMap<String, CompletableFuture<TRESP>>>()

    fun addFuture(correlationId: String, future: CompletableFuture<TRESP>, partition: Int) {
        if (futuresInPartitionMap[partition].isNullOrEmpty()) {
            val futureMap = WeakValueHashMap<String, CompletableFuture<TRESP>>()
            futureMap[correlationId] = future
            futuresInPartitionMap[partition] = futureMap
        } else {
            futuresInPartitionMap[partition]?.put(correlationId, future)
        }
    }

    fun getFuture(correlationId: String, partition: Int): CompletableFuture<TRESP>? {
        return futuresInPartitionMap[partition]?.get(correlationId)
    }

    fun removeFuture(correlationId: String, partition: Int) {
        futuresInPartitionMap[partition]?.remove(correlationId)
    }

    fun removePartitions(partitions: List<TopicPartition>) {
        for (partition in partitions) {
            val futures = futuresInPartitionMap[partition.partition()]
            for (key in futures!!.keys) {
                futures[key]?.completeExceptionally(
                    CordaRPCAPISenderException(
                        "Repartition event!! Results for this future " +
                                "can no longer be returned"
                    )
                )
            }
            futuresInPartitionMap.remove(partition.partition())
        }
    }

}