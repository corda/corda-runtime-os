package net.corda.messaging.subscription.consumer

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class StateAndEventConsumerImpl2<K : Any, E : Any, S : Any>(
    private val processor: StateAndEventProcessor<K, S, E>,
    private val eventSource: EventSource<K, E>,
    private val stateCache: StateCache<K, S>,
    private val pollingLoopExecutor: ExecutorService,
    private val cordaPublisher: Publisher
) : AutoCloseable {

    private var poller: CompletableFuture<Unit>? = null
    private var isRunning = false

    fun start() {
        stateCache.subscribe(::onCacheReady)
    }

    override fun close() {
        isRunning = false
        poller?.get(60, TimeUnit.SECONDS)
        eventSource.close()
    }

    private fun onCacheReady() {
        eventSource.start(stateCache::getMaxOffsetsByPartition, stateCache::isOffsetGreaterThanMaxSeen)
        poller = CompletableFuture.supplyAsync(::pollingLoop, pollingLoopExecutor)
    }

    private fun pollingLoop() {
        isRunning = true
        while (isRunning) {
            val block = eventSource.nextBlock(500)
            if (block.isNotEmpty()) {
                // divide block by key to prevent event per key re-ordering, then process, update the state and publish
                // the results for each event asynchronously
                block.splitByKey().forEach {
                    it.map(::processEventAsync)
                        .awaitAll(60, TimeUnit.SECONDS)
                }
            }
        }
    }

    private fun processEventAsync(sourceRecord: EventSourceRecord<K, E>)
            : CompletableFuture<Unit> {
        val state = stateCache.get(sourceRecord.key)
        return CompletableFuture.supplyAsync {
            val result = processor.onNext(state, sourceRecord.record)

            stateCache.write(
                sourceRecord.key,
                result.updatedState,
                StateCache.LastEvent(sourceRecord.topic, sourceRecord.partition, sourceRecord.offset)
            ).thenApply {
                cordaPublisher.publish(result.responseEvents).awaitAll(60, TimeUnit.SECONDS)
            }
        }
    }

    fun <V> Collection<CompletableFuture<out V>>.awaitAll(timeout: Long, timeUnit: TimeUnit) {
        if (isEmpty()) return
        CompletableFuture.allOf(*toTypedArray()).get(timeout, timeUnit)
    }

    private fun List<EventSourceRecord<K, E>>.splitByKey(): List<List<EventSourceRecord<K, E>>> {
        val output = mutableListOf<List<EventSourceRecord<K, E>>>()
        var currentBatch = mutableListOf<EventSourceRecord<K, E>>()
        var seenKeys = HashSet<K>()
        for (event in this) {
            if (seenKeys.contains(event.key)) {
                seenKeys.clear()
                output.add(currentBatch)
                currentBatch = mutableListOf()
            }
            currentBatch.add(event)
            seenKeys.add(event.key)
        }

        if (currentBatch.isNotEmpty()) {
            output.add(currentBatch)
        }

        return output
    }
}