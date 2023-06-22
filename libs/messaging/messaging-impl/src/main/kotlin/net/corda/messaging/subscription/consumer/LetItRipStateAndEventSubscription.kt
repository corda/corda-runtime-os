package net.corda.messaging.subscription.consumer

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.ThreadLooper
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class LetItRipStateAndEventSubscription<K : Any, E : Any, S : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val eventSource: EventSource<K, E>,
    private val stateCache: StateCache<K, S>,
    private val pollingLoopExecutor: ExecutorService,
    private val cordaPublisher: Publisher,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : StateAndEventSubscription<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    private var threadLooper = ThreadLooper(
        log,
        config,
        lifecycleCoordinatorFactory,
        "state/event processing thread",
        ::pollingLoop
    )

    override fun start() {
        stateCache.subscribe(::onCacheReady)
    }

    override fun close() {
        threadLooper.close()
        eventSource.close()
    }

    private fun onCacheReady() {
        eventSource.start(stateCache::getMinOffsetsByPartition, stateCache::isOffsetGreaterThanMaxSeen)
        threadLooper.start()
    }

    private fun pollingLoop() {
        while ( threadLooper.isRunning ) {
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
        val record = sourceRecord.record
        val state = stateCache.get(record.key)
        return CompletableFuture.supplyAsync {
            val result = processor.onNext(state, sourceRecord.record)

            stateCache.write(
                record.key,
                result.updatedState,
                StateCache.LastEvent(
                    record.topic,
                    sourceRecord.partition,
                    sourceRecord.offset,
                    sourceRecord.safeMinOffset
                )
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
            if (seenKeys.contains(event.record.key)) {
                seenKeys.clear()
                output.add(currentBatch)
                currentBatch = mutableListOf()
            }
            currentBatch.add(event)
            seenKeys.add(event.record.key)
        }

        if (currentBatch.isNotEmpty()) {
            output.add(currentBatch)
        }

        return output
    }
}