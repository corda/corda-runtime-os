package net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class RPCConsumerRebalanceListener<TRESP>(
    private val topic: String,
    private val groupName: String
) : ConsumerRebalanceListener {

    var partitions = mutableListOf<TopicPartition>()
    var futuresInPartitionMap = ConcurrentHashMap<Int, MutableList<CompletableFuture<TRESP>>>()

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        for (partition in partitions) {
            for (future in futuresInPartitionMap[partition.partition()]!!) {
                future.completeExceptionally(
                    CordaRPCAPISenderException(
                        "Repartition event!! Results for this future " +
                                "can no longer be returned"
                    )
                )
            }
            futuresInPartitionMap.remove(partition.partition())
        }
        this.partitions.removeAll(partitions)
        val partitionIds = partitions.map { it.partition() }.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition revoked: $partitionIds.")
    }

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        this.partitions.addAll(partitions)
        for (partition in partitions) {
            futuresInPartitionMap[partition.partition()] = mutableListOf()
        }
        val partitionIds = partitions.map { it.partition() }.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition assigned: $partitionIds.")
    }
}