package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.subscription.consumer.EventSource
import net.corda.messaging.subscription.consumer.EventSourceRecord
import net.corda.messaging.subscription.consumer.StateCache
import net.corda.messaging.utils.toCordaProducerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
class LetItRipStateAndEventSubscription<K : Any, E : Any, S : Any>(
    subscriptionId: String,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val eventSource: EventSource<K, E>,
    private val stateCache: StateCache<K, S>,
    private val cordaPublisher: CordaProducer,
    private val pollingLoopExecutor: ExecutorService,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : StateAndEventSubscription<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${subscriptionId}")
    private var poller: CompletableFuture<Unit>? = null
    private var isRunning = false
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(
            LifecycleCoordinatorName(
                "LetItRipStateAndEventSubscription",
                subscriptionId
            )
        ) { _, _ -> }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun start() {
        stateCache.subscribe(::onCacheReady)
    }

    override fun close() {
        eventSource.close()
        poller?.get(60, TimeUnit.SECONDS)
    }

    private fun onCacheReady() {
        eventSource.start(stateCache::getMinOffsetsByPartition, stateCache::isOffsetGreaterThanMaxSeen)
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
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

    private fun processEventAsync(sourceRecord: EventSourceRecord<K, E>): CompletableFuture<Unit> {
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
                result.responseEvents.map {
                    val fut = CompletableFuture<Unit>()
                    cordaPublisher.send(it.toCordaProducerRecord()) { ex ->
                        setFutureFromResponse(ex, fut)
                    }
                    fut
                }.awaitAll(60, TimeUnit.SECONDS)
            }
        }
    }

    @Suppress("SpreadOperator")
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

    private fun setFutureFromResponse(exception: Exception?, future: CompletableFuture<Unit>) {
        when (exception) {
            null -> {
                future.complete(Unit)
            }

            else -> {
                log.warn("Failed to publish message", exception)
            }
        }
    }
}