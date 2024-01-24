package net.corda.messaging.mediator.slim

import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.mediator.processor.EventProcessingOutput
import net.corda.messaging.mediator.processor.StateChangeAndOperation
import net.corda.tracing.wrapWithTracingExecutor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue

class SlimEventProcessor<K : Any, V : Any>(
    private val stateService: SlimStateService,
    private val eventExecutor: SlimEventExecutor<K, V>,
    private val executor: ExecutorService,
) {
    companion object {
        val MAX_QUEUE_DEPTH = 20000
    }

    private val futureMap = ConcurrentHashMap<K, CompletableFuture<Unit>>()
    private val futureQueue = LinkedBlockingQueue<CompletableFuture<Unit>>(MAX_QUEUE_DEPTH)
    private val tracedExecutor = wrapWithTracingExecutor(executor)
    fun enqueueEvents(events: List<CordaConsumerRecord<K, V>>) {
        events.forEach { event ->
            // This code is keeping a map of futures per key, if we get a new future for a key with an existing one
            // we chain them together to ensure events for the same key are processed in order and sequentially.
            // when an event has completed processing it attempts to remove itself from the map to keep it cleaned up
            val eventProcessingFuture = futureMap.compute(event.key) { key, current ->
                if (current == null) {
                    val f = CompletableFuture.supplyAsync({ processEvent(event) }, tracedExecutor)
                    f.thenRun { futureMap.remove(key, f) }
                    f
                } else {
                    val f = current.thenApplyAsync({ processEvent(event) }, tracedExecutor)
                    f.thenRun { futureMap.remove(key, f) }
                    f
                }
            }

            // Blocks if we have too many events in flight
            futureQueue.put(eventProcessingFuture)
        }
    }

    private fun processEvent(event: CordaConsumerRecord<K, V>) {
        // Get the state
        val state = stateService.getState(event.key.toString()).get()

        // process the event
        val result = eventExecutor.execute(state, event)

        // persist the state
        when (val op = result.stateChangeAndOperation) {
            is StateChangeAndOperation.Create -> stateService.createState(op.outputState)
            is StateChangeAndOperation.Update -> stateService.createState(op.outputState)
            is StateChangeAndOperation.Delete -> stateService.createState(op.outputState)
            is StateChangeAndOperation.Noop -> CompletableFuture<Unit>().apply { this.complete(Unit) }
        }.get()

        // output the events
    }
}

class SlimEventExecutor<K : Any, V : Any> {
    fun execute(state: State?, event: CordaConsumerRecord<K, V>): EventProcessingOutput {
    }
}
