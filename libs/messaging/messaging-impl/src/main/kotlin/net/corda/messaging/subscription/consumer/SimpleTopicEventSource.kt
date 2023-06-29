package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.utils.toRecord
import java.util.concurrent.ExecutorService

class SimpleTopicEventSource<K : Any, E : Any>(
    private val config: SimpleConsumerConfig,
    private val consumer: CordaConsumer<K, E>,
    private val pollingLoopExecutor: ExecutorService,
) : EventSource<K, E> {

    private var internalOffsetFilter: (Int, Long) -> Boolean = ::nullFilter

    override fun start(nextOffsetSupplier: (List<Int>) -> Map<Int, Long>, offsetFilter: (Int, Long) -> Boolean) {
        internalOffsetFilter = offsetFilter
        consumer.subscribe(config.topic)
        val assignedPartitions = consumer.assignment()
        val offsets = nextOffsetSupplier(assignedPartitions.map { it.partition })
        for (assignment in assignedPartitions) {
            consumer.seek(assignment, offsets[assignment.partition] ?: 0)
        }
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
}