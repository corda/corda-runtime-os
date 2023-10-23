package net.corda.messaging.mediator

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
    private val timeout: Duration,
    maxPollRecords: Int = DEFAULT_MAX_POLL_RECORDS,
): MediatorConsumer<K, V> {
    companion object {
        private const val DEFAULT_MAX_POLL_RECORDS = 500
        private const val BUFFER_QUEUE_TIMEOUT_MILLIS = 100L
    }

    private val bufferedRecords = LinkedBlockingDeque<CordaConsumerRecord<K, V>>(maxPollRecords)
    private var lastReturnedOffsets = mutableMapOf<Int, Long>()
    private val lastReturnedOffsetsLock = ReentrantLock()
    private var pendingCommit = AtomicBoolean(false)
    private val pendingResetOffsetPosition = AtomicBoolean(false)
    private var stopped = AtomicBoolean(false)
    private var consumerThread: CompletableFuture<Unit>? = null
    private var dataAvailableCallback = AtomicReference<(() -> Unit)?>()

    private class CallbackWrapper(private val callback: () -> Unit) {
        private val called = AtomicBoolean(false)
        fun call() {
            if (called.compareAndSet(false, true)) {
                callback.invoke()
            }
        }
    }

    private fun run() {
        while (!stopped.get()) {
            pollConsumer()
            commitIfNeeded()
        }
        commitIfNeeded()
    }

    override fun subscribe() {
        consumer.subscribe(topic)
        consumerThread = execute(::run)
    }

    override fun setDataAvailableCallback(callback: () -> Unit) {
        dataAvailableCallback.set(CallbackWrapper(callback)::call)
        if (bufferedRecords.peek() != null) {
            dataAvailableCallback.get()?.invoke()
        }
    }

    override fun poll(): List<CordaConsumerRecord<K, V>> = pollInternal()

    override fun syncCommitOffsets() = commitInternal()

    override fun resetEventOffsetPosition() = resetEventOffsetPositionInternal()

    override fun close() {
        stopped.set(true)
        consumerThread?.join()
        consumer.close()
    }

    /**
     * Returns true is max records polled
     */
    private fun pollConsumer() {
        if (pendingResetOffsetPosition.get()) {
            consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
            pendingResetOffsetPosition.set(false)
        }
        val records = consumer.poll(timeout).iterator()
        if (stopped.get()) {
            return
        }
        while (records.hasNext()) {
            var recordAdded = false
            while (records.hasNext() && bufferedRecords.offer(records.next())) {
                recordAdded = true
            }
            if (recordAdded) {
                dataAvailableCallback.get()?.invoke()
            }
            if (records.hasNext()) {
                if (bufferedRecords.offer(records.next(), BUFFER_QUEUE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    dataAvailableCallback.get()?.invoke()
                }
                if (stopped.get()) {
                    return
                }
            }
        }
    }

    private fun pollInternal(): List<CordaConsumerRecord<K, V>> {
        val records = mutableListOf<CordaConsumerRecord<K, V>>()
        lastReturnedOffsetsLock.withLock {
            while (true) {
                val record = bufferedRecords.poll() ?: break
                lastReturnedOffsets[record.partition] = record.offset
                records.add(record)

            }
        }
        return records
    }

    private fun commitInternal() {
        pendingCommit.set(true)
    }

    private fun commitIfNeeded() {
        if (pendingCommit.get()) {
            lastReturnedOffsetsLock.withLock {
                consumer.syncCommitOffsets(topic, lastReturnedOffsets)
                pendingCommit.set(false)
            }
        }
    }

    private fun resetEventOffsetPositionInternal() {
        pendingResetOffsetPosition.set(true)
    }


    // TODO Use TaskManager
    private fun execute(command: () -> Unit): CompletableFuture<Unit> {
        val uniqueId = UUID.randomUUID().toString().takeLast(8)
        val result = CompletableFuture<Unit>()
        thread(
            start = true,
            isDaemon = true,
            contextClassLoader = null,
            name = "MessageBusConsumer-$topic-$uniqueId",
            priority = -1,
        ) {
            try {
                result.complete(command())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        return result
    }
}