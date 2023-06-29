package net.corda.messaging.subscription.consumer

import java.util.TreeSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit

class OrderedQueueEventSource<K : Any, E : Any>(
    private val source: EventSource<K, E>,
    private val pollingLoopExecutor: ExecutorService,
) : EventSource<K, E> {

    private data class QueueEntry<K : Any, E : Any>(val priorityTimestamp: Long, val record: EventSourceRecord<K, E>)

    private var poller: CompletableFuture<Unit>? = null
    private var isRunning = false
    private val seenOffsets = TreeSet<Long>()
    private val orderedQueue = PriorityBlockingQueue<QueueEntry<K, E>>(11) { a, b ->
        a.priorityTimestamp.compareTo(b.priorityTimestamp)
    }

    override fun start(nextOffsetSupplier: (List<Int>) -> Map<Int, Long>, offsetFilter: (Int, Long) -> Boolean) {
        source.start(nextOffsetSupplier, offsetFilter)
        poller = CompletableFuture.supplyAsync(::pollingLoop, pollingLoopExecutor)
    }

    override fun nextBlock(maxBatchSize: Int): List<EventSourceRecord<K, E>> {
        val outputRecords = mutableListOf<QueueEntry<K, E>>()
        orderedQueue.drainTo(outputRecords, maxBatchSize)
        return outputRecords.map {
            val outputEventRecord = it.record.copy(safeMinOffset = seenOffsets.first())
            seenOffsets.remove(it.record.offset)
            outputEventRecord
        }
    }

    override fun close() {
        isRunning = false
        poller?.get(60, TimeUnit.SECONDS)
        source.close()
    }

    private fun pollingLoop() {
        isRunning = true
        while (isRunning) {
            val sourceEventRecords = source.nextBlock(500).map(::getPriorityTimestampAndRecord)
            if (sourceEventRecords.isNotEmpty()) {
                sourceEventRecords.forEach {
                    seenOffsets.add(it.record.offset)
                    orderedQueue.offer(it)
                }
            }
        }
    }

    private fun getPriorityTimestampAndRecord(sourceEvent: EventSourceRecord<K, E>): QueueEntry<K, E> {
        val record = sourceEvent.record
        val header = record.headers.firstOrNull { it.first == "corda.priority.timestamp" }
        return if (header == null) {
            val time = System.nanoTime()
            val newHeaders = record.headers + Pair("corda.priority.timestamp", time.toString())
            QueueEntry(System.nanoTime(), sourceEvent.copy(record = record.copy(headers = newHeaders)))
        } else {
            QueueEntry(header.second.toLong(), sourceEvent)
        }
    }
}