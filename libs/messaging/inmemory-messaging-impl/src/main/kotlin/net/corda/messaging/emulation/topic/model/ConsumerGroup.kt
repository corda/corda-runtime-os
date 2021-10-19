package net.corda.messaging.emulation.topic.model

import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

internal class ConsumerGroup(
    private val partitions: Collection<Partition>,
    internal val subscriptionConfig: SubscriptionConfiguration,
    firstConsumer: Consumer,
    internal val lock: ReentrantReadWriteLock = ReentrantReadWriteLock(),
) {
    private val consumers = ConcurrentHashMap<Consumer, ConsumptionLoop>()
    private val commitments = ConcurrentHashMap<Partition, Long>()

    private val commitStrategy = firstConsumer.commitStrategy
    private val partitionStrategy = firstConsumer.partitionStrategy
    private val offsetStrategy = firstConsumer.offsetStrategy

    private val phase = AtomicInteger(0)
    private val newData = lock.writeLock().newCondition()

    internal val pollSizePerPartition = (subscriptionConfig.maxPollSize / partitions.size).coerceAtLeast(1)

    class DuplicateConsumerException : Exception("Can not consume the same consumer twice")

    internal fun currentPhase() = phase.get()

    internal fun waitForPhaseChange(knownPhase: Int) {
        lock.write {
            if (phase.get() == knownPhase) {
                newData.await(1, TimeUnit.HOURS)
            }
        }
    }

    fun addPartitionsToLoop(loop: ConsumptionLoop, partitions: Collection<Partition>) {
        val partitionToOffset = partitions.associateWith { partition ->
            commitments.computeIfAbsent(partition) {
                when (offsetStrategy) {
                    OffsetStrategy.LATEST -> partition.latestOffset()
                    OffsetStrategy.EARLIEST -> 0L
                }
            }
        }
        loop.addPartitions(partitionToOffset)
    }

    fun commit(commits: Map<Partition, Long>) {
        commitments += commits
    }

    fun stopConsuming(consumer: Consumer) {
        val loop = consumers.remove(consumer)
        if (loop != null) {
            loop.removePartitions(loop.partitions)
            if (consumers.isNotEmpty()) {
                repartition()
            } else {
                wakeUp()
            }
        }
    }

    fun assignPartition(consumer: Consumer, partitions: Collection<Partition>) {
        if (partitionStrategy != PartitionStrategy.MANUAL) {
            throw IllegalStateException("Can not manually assign partitions to this type of consumer")
        }
        lock.write {
            val loop = consumers[consumer] ?: throw IllegalStateException("No such consumer")
            addPartitionsToLoop(loop, partitions)
            wakeUp()
        }
    }

    fun unAssignPartition(consumer: Consumer, partitions: Collection<Partition>) {
        if (partitionStrategy != PartitionStrategy.MANUAL) {
            throw IllegalStateException("Can not manually un assign partitions to this type of consumer")
        }
        lock.write {
            val loop = consumers[consumer] ?: throw IllegalStateException("No such consumer")
            loop.removePartitions(partitions)

            wakeUp()
        }
    }

    fun isConsuming(consumer: Consumer): Boolean {
        return consumers.containsKey(consumer)
    }

    internal fun wakeUp() {
        lock.write {
            phase.incrementAndGet()
            newData.signalAll()
        }
    }

    private fun repartition() {
        when (partitionStrategy) {
            PartitionStrategy.DIVIDE_PARTITIONS -> repartitionDivision()
            PartitionStrategy.SHARE_PARTITIONS -> repartitionShare()
            PartitionStrategy.MANUAL -> {
                // Do nothing
            }
        }
    }

    private fun repartitionShare() {
        lock.write {
            val loopsWithoutPartitions = consumers.filterValues {
                it.partitions.isEmpty()
            }
            loopsWithoutPartitions.values.forEach { loop ->
                addPartitionsToLoop(loop, partitions)
            }

            wakeUp()
        }
    }

    private fun repartitionDivision() {
        lock.write {
            val consumersWithAssignedPartitions = partitions.withIndex().groupBy({
                it.index % consumers.size
            }, {
                it.value
            }).values
                .zip(consumers.entries).onEach { (newPartitionList, consumerAndLoop) ->
                    val oldPartitionList = consumerAndLoop.value.partitions
                    val unassigned = oldPartitionList - newPartitionList
                    val assigned = newPartitionList - oldPartitionList
                    consumerAndLoop.value.removePartitions(unassigned)
                    if (assigned.isNotEmpty()) {
                        addPartitionsToLoop(consumerAndLoop.value, assigned)
                    }
                }.map {
                    it.second.key
                }

            val consumersWithoutAssignedPartitions = consumers - consumersWithAssignedPartitions
            consumersWithoutAssignedPartitions.forEach { consumerAndLoop ->
                consumerAndLoop.value.removePartitions(consumerAndLoop.value.partitions)
            }
            wakeUp()
        }
    }

    fun createConsumption(
        consumer: Consumer,
    ): Consumption {
        if ((consumer.partitionStrategy != partitionStrategy) ||
            (consumer.commitStrategy != commitStrategy) ||
            (consumer.offsetStrategy != offsetStrategy)
        ) {
            throw IllegalStateException("Can not subscribe two different consumer types to the same group")
        }
        val loop = consumers.compute(consumer) { _, currentPartitions ->
            if (currentPartitions != null) {
                throw DuplicateConsumerException()
            }
            ConsumptionLoop(consumer, this)
        }
        repartition()
        return ConsumptionThread(
            threadName =
            "consumer thread ${consumer.groupName}-${consumer.topicName}:${consumer.hashCode()}",
            timeout = subscriptionConfig.threadStopTimeout,
            killMe = {
                this.stopConsuming(consumer)
            },
            loop = loop!!
        )
    }
}
