package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messaging.utils.toRecord
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class SimpleTopicEventSource<K : Any, E : Any>(
    private val config: SimpleConsumerConfig,
    private val consumer: CordaConsumer<K, E>,
    private val pollingLoopExecutor: ExecutorService,
) : EventSource<K, E> {

    private var internalOffsetFilter: (Int, Long) -> Boolean = ::nullFilter

    override fun start(nextOffsetSupplier: (List<Int>) -> Map<Int, Long>, offsetFilter: (Int, Long) -> Boolean) {
        internalOffsetFilter = offsetFilter
        val assignmentListener = AssignmentListener { assignments ->
            val offsets = nextOffsetSupplier(assignments.map { it.partition })
            for (assignment in assignments) {
                consumer.seek(assignment, offsets[assignment.partition] ?: 0)
            }
        }

        consumer.subscribe(config.topic, assignmentListener)
    }

    override fun nextBlock(maxBatchSize: Int): List<EventSourceRecord<K, E>> {
        val records = consumer.poll(config.pollTimeout)
        return records.filter { internalOffsetFilter(it.partition, it.offset) }
            .map { EventSourceRecord(it.partition, it.offset, it.offset, it.toRecord()) }
    }

    override fun close() {
        consumer.close()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun nullFilter(partition: Int, offset: Long): Boolean {
        return false
    }

    private class AssignmentListener(private val onNewAssignment: (List<CordaTopicPartition>) -> Unit) :
        CordaConsumerRebalanceListener {
        private val assignedPartitions = mutableSetOf<CordaTopicPartition>()

        override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
            partitions.forEach { assignedPartitions.remove(it) }
        }

        override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
            val newPartitions = partitions.filterNot { assignedPartitions.contains(it) }

            assignedPartitions.addAll(newPartitions)

            onNewAssignment(assignedPartitions.toList())
        }
    }
}