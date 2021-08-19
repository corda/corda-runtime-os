package net.corda.messaging.emulation.topic.model

import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class ConsumerGroup(
    private val topicName: String,
    private val partitions: Collection<Partition>,
    internal val subscriptionConfig: SubscriptionConfiguration,
    private val lock: ReentrantReadWriteLock = ReentrantReadWriteLock(),
) {
    private val consumers = ConcurrentHashMap<Consumer, Collection<Partition>>()
    private val commitments = ConcurrentHashMap<Partition, Long>()

    private val newData = lock.writeLock().newCondition()

    class DuplicateConsumerException : Exception("Can not consume the same consumer twice")

    internal fun waitForData() {
        lock.write {
            newData.await(1, TimeUnit.HOURS)
        }
    }

    internal fun getPartitions(consumer: Consumer): Collection<Pair<Partition, Long>> {
        return lock.read {
            consumers[consumer]?.map { partition ->
                val offset = commitments.computeIfAbsent(partition) {
                    when (consumer.offsetStrategy) {
                        OffsetStrategy.LATEST -> partition.latestOffset()
                        OffsetStrategy.EARLIEST -> 0L
                    }
                }
                partition to offset
            } ?: emptyList()
        }
    }

    fun stopConsuming(consumer: Consumer) {
        val partitions = consumers.remove(consumer)
        if (partitions != null) {
            consumer.partitionAssignmentListener?.onPartitionsUnassigned(partitions.map { topicName to it.partitionId })
            if (consumers.isNotEmpty()) {
                repartition()
            } else {
                wakeUp()
            }
        }
    }

    fun commitRecord(partition: Partition, records: Collection<RecordMetadata>) {
        commitments[partition] = records.maxOf { it.offset } + 1
    }

    fun isConsuming(consumer: Consumer): Boolean {
        return consumers.containsKey(consumer)
    }

    internal fun wakeUp() {
        lock.write {
            newData.signalAll()
        }
    }

    private fun repartition() {
        lock.write {
            val consumersWithAssignedPartitions = partitions.withIndex().groupBy({
                it.index % consumers.size
            }, {
                it.value
            }).values
                .zip(consumers.keys).onEach { (newPartitionList, consumer) ->
                    val listener = consumer.partitionAssignmentListener
                    if (listener != null) {
                        val oldPartitionList = consumers[consumer] ?: emptyList()
                        val assigned = newPartitionList - oldPartitionList
                        val unassigned = oldPartitionList - newPartitionList
                        if (unassigned.isNotEmpty()) {
                            listener.onPartitionsUnassigned(unassigned.map { topicName to it.partitionId })
                        }
                        if (assigned.isNotEmpty()) {
                            listener.onPartitionsAssigned(assigned.map { topicName to it.partitionId })
                        }
                    }
                    consumers[consumer] = newPartitionList
                }.map {
                    it.second
                }

            val consumersWithoutAssignedPartitions = consumers.keys - consumersWithAssignedPartitions
            consumersWithoutAssignedPartitions.forEach { consumer ->
                val unassigned = consumers.put(consumer, emptyList())
                if (unassigned?.isNotEmpty() == true) {
                    consumer.partitionAssignmentListener?.onPartitionsUnassigned(unassigned.map { topicName to it.partitionId })
                }
            }
            newData.signalAll()
        }
    }

    fun createConsumption(
        consumer: Consumer,
    ): Consumption {
        consumers.compute(consumer) { _, currentPartitions ->
            if (currentPartitions != null) {
                throw DuplicateConsumerException()
            }
            ConcurrentHashMap.newKeySet()
        }
        repartition()
        return ConsumptionThread(
            threadName =
            "consumer thread ${consumer.groupName}-${consumer.topicName}:${consumer.hashCode()}",
            timeout = subscriptionConfig.threadStopTimeout,
            killMe = {
                this.stopConsuming(consumer)
            },
            loop = ConsumptionLoop(consumer, this)
        )
    }
}
