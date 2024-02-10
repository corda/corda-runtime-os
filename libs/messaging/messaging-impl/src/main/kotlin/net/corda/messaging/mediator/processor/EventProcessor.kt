package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.MediatorInputService
import net.corda.messaging.api.mediator.MediatorInputService.Companion.INPUT_HASH_HEADER
import net.corda.messaging.api.mediator.MediatorInputService.Companion.SYNC_RESPONSE_HEADER
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.tracing.addTraceContextToMediatorMessage
import net.corda.tracing.addTraceContextToRecord
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Class to process records received from the consumer.
 * Passes each record to process along with its state to the [config]s [StateAndEventProcessor].
 * Synchronous outputs from the processor are sent immediately and the responses are processed as new inputs.
 * Asynchronous outputs destined for the message bus and states to be saved to the state manager are returned.
 *
 */
class EventProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val stateManagerHelper: StateManagerHelper<S>,
    private val messageRouter: MessageRouter,
    private val mediatorInputService: MediatorInputService,
    private val executor: Executor,
) {
    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    fun processEvents(
        key: K,
        inputRecords: List<Record<K, E>>,
        state: State?,
    ) {
        val context = EventContext<K, S>(key, state)
        val future = CompletableFuture<EventContext<K, S>>()
        CompletableFuture.supplyAsync({
            log.info("Processing events for key ${context.key}")
            val persistedState = context.inputState
            context.processorState = stateManagerHelper.deserializeValue(persistedState)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    persistedState?.metadata
                )
            }
            processEvent(
                events = ArrayDeque(
                    inputRecords.map { it.withHeader(INPUT_HASH_HEADER, mediatorInputService.getHash(it)) }
                ),
                onFinished = { future.complete(context) },
                context
            )
        }, executor)
        future.thenCompose {
            persistState(context)
        }.thenCompose {
            sendAsyncEvents(context)
        }
    }

    private fun processEvent(
        events: Deque<Record<K, E>>,
        onFinished: () -> Unit,
        context: EventContext<K, S>,
    ) {
        if (events.isEmpty()) {
            onFinished()
            return
        }
        CompletableFuture.supplyAsync({
            val event = events.removeFirst()
//            log.info("Processing event ${event.value?.javaClass?.simpleName} for key ${context.key}")
            val response = config.messageProcessor.onNext(context.processorState, event)
//            log.info("Got ${response.responseEvents.size} response events")
            context.processorState = response.updatedState
            val (syncEvents, asyncEvents) = response.responseEvents.map { convertToMessage(it) }.partition {
                messageRouter.getDestination(it).type == RoutingDestination.Type.SYNCHRONOUS
            }
//            log.info("Got ${syncEvents.size} sync and ${asyncEvents.size} async events")
            context.asyncOutputs.addAll(asyncEvents)
            val inputInventHash = event.header(INPUT_HASH_HEADER)!!
            Pair(syncEvents, inputInventHash)
        }, executor).thenCompose { (syncEvents, inputInventHash) ->
            processSyncEvents(context.key, syncEvents, inputInventHash)
        }.thenApply { syncEventResponses ->
            syncEventResponses.forEach {
                events.addFirst(it)
            }
            processEvent(events, onFinished, context)
        }
    }

    private fun processSyncEvents(
        key: K,
        syncEvents: List<MediatorMessage<Any>>,
        inputInventHash: String,
    ): CompletableFuture<List<Record<K, E>>> {
        if (syncEvents.isEmpty()) {
            return CompletableFuture.completedFuture(emptyList())
        }
//        log.info("=> processSyncEvents for key $key")
        val syncResultFutures = syncEvents.map { processSyncEvent(it) }
        @Suppress("SpreadOperator")
        return CompletableFuture.allOf(*syncResultFutures.toTypedArray()).thenApply {
//            log.info("Processing sync events for key $key")
            syncResultFutures.mapNotNull { it.join() }
                .map { it.toRecord(key, inputInventHash) }
        }
    }

    private fun processSyncEvent(
        message: MediatorMessage<Any>
    ): CompletableFuture<MediatorMessage<E>?> {
        val destination = messageRouter.getDestination(message)
        @Suppress("UNCHECKED_CAST")
        return with(destination) {
            message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
            client.send(message)
        }.thenApply {
            it?.let {
                addTraceContextToMediatorMessage(it) as MediatorMessage<E>?
            }
        }
    }

    private fun persistState(
        context: EventContext<K, S>,
    ): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync({
//            log.info("Persisting state for key ${context.key}")
            val newState = stateManagerHelper.createOrUpdateState(
                context.key.toString(),
                context.inputState,
                context.processorState
            )
            val stateChangeAndOperation = StateChangeAndOperation.create(context.inputState, newState)
            stateManagerHelper.persistState(stateChangeAndOperation)
        }, executor)
    }

    private fun sendAsyncEvents(
        context: EventContext<K, S>,
    ): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync({
//            log.info("Sending ${context.asyncOutputs.size} async events for key ${context.key}")
            context.asyncOutputs.forEach { message ->
                with(messageRouter.getDestination(message)) {
                    message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                    client.send(message)
                }
            }
        }, executor)
    }

    private fun convertToMessage(record: Record<*, *>): MediatorMessage<Any> {
        return MediatorMessage(
            record.value!!,
            record.headers.toMessageProperties().also { properties ->
                properties[MessagingClient.MSG_PROP_KEY] = record.key
                if (record.topic != null && record.topic!!.isNotEmpty()) {
                    properties[MessagingClient.MSG_PROP_TOPIC] = record.topic!!
                }
            },
        )
    }

    private fun List<Pair<String, String>>.toMessageProperties() =
        associateTo(mutableMapOf()) { (key, value) -> key to (value as Any) }

    private fun MediatorMessage<E>.toRecord(key: K, eventHash: String) =
        addTraceContextToRecord(
            Record(
                "",
                key,
                payload,
                0,
                listOf(
                    SYNC_RESPONSE_HEADER to "true",
                    INPUT_HASH_HEADER to eventHash
                )
            ),
            properties
        )
}