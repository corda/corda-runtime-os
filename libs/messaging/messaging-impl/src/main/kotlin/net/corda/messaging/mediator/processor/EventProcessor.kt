package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
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
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.tracing.addTraceContextToRecord

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
) {

    private val metrics = EventMediatorMetrics(config.name)

    /**
     * Process a group of events.
     *
     * When the mediator is configured with an idempotence processor, and a duplicate or replayed record is detected, based on a hash of
     * the record payload, then the asynchronous outputs from the previous invocation  of the processor will be retrieved
     * from the [MediatorState].In this case, the message processor is not executed again,
     * and the previously retrieved outputs are returned for resending.
     *
     * Otherwise, the message processor is executed and any synchronous calls are sent and responses are processed immediately.
     *
     * Finally, any asynchronous outputs are returned, as well as the [State] object to update.
     *
     * @param inputs Group of records of various keys along with their respective states
     * @return The asynchronous outputs and state updates grouped by record key
     */
    fun processEvents(
        inputs: Map<K, EventProcessingInput<K, E>>
    ): Map<K, EventProcessingOutput> {
        return inputs.mapValues { (key, input) ->
            val inputState = input.state
            val allConsumerInputs = input.records
            val processorState = stateManagerHelper.deserializeValue(inputState)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    inputState?.metadata
                )
            }

            val newInputs = EventProcessingInput(key, allConsumerInputs, inputState)
            processRecords(newInputs, processorState)
        }
    }

    private fun processRecords(
        input: EventProcessingInput<K, E>,
        inputProcessorState: StateAndEventProcessor.State<S>?,
    ): EventProcessingOutput {
        val key = input.key
        val inputState = input.state
        var processorState = inputProcessorState
        val asyncOutputs = mutableMapOf<Record<K, E>, MutableList<MediatorMessage<Any>>>()
        val processed = try {
            input.records.forEach { consumerInputEvent ->
                val (updatedProcessorState, newAsyncOutputs) = processConsumerInput(consumerInputEvent, processorState, key)
                processorState = updatedProcessorState
                asyncOutputs.addOutputs(consumerInputEvent, newAsyncOutputs)
            }
            stateManagerHelper.createOrUpdateState(key.toString(), inputState, processorState)
        } catch (e: EventProcessorSyncEventsIntermittentException) {
            // If an intermittent error occurs here, the RPC client has failed to deliver a message to another part
            // of the system despite the retry loop implemented there. The exception may contain any state that has been output
            // from the processing of consumer input.
            metrics.eventProcessorFailureCounter.increment()
            asyncOutputs.clear()
            stateManagerHelper.failStateProcessing(
                key.toString(),
                getMostRecentState(key, e.partiallyProcessedState, processorState, inputState),
                "unable to contact Corda services while processing events"
            )
        }

        val stateChangeAndOperation = stateChangeAndOperation(inputState, processed)
        return EventProcessingOutput(asyncOutputs.values.flatten(), stateChangeAndOperation)
    }

    private fun getMostRecentState(
        key: K,
        partiallyProcessedState: StateAndEventProcessor.State<*>?,
        currentProcessorState: StateAndEventProcessor.State<S>?,
        inputState: State?
    ): State? {
        return when {
            partiallyProcessedState != null -> {
                @Suppress("unchecked_cast")
                stateManagerHelper.createOrUpdateState(
                    key.toString(),
                    inputState,
                    partiallyProcessedState as StateAndEventProcessor.State<S>
                )
            }

            currentProcessorState != null -> stateManagerHelper.createOrUpdateState(key.toString(), inputState, currentProcessorState)

            else -> inputState
        }
    }

    private fun processConsumerInput(
        consumerInputEvent: Record<K, E>,
        processorState: StateAndEventProcessor.State<S>?,
        key: K,
    ): Pair<StateAndEventProcessor.State<S>?, List<MediatorMessage<Any>>> {
        var processorStateUpdated = processorState
        val newAsyncOutputs = mutableListOf<MediatorMessage<Any>>()
        val consumerInputHash = mediatorInputService.getHash(consumerInputEvent)
        val queue = ArrayDeque(listOf(consumerInputEvent))
        while (queue.isNotEmpty()) {
            val event = getNextEvent(queue, consumerInputHash)
            val response = config.messageProcessor.onNext(processorStateUpdated, event)
            processorStateUpdated = response.updatedState
            val (syncEvents, asyncEvents) = response.responseEvents.map { convertToMessage(it) }.partition {
                messageRouter.getDestination(it).type == RoutingDestination.Type.SYNCHRONOUS
            }
            newAsyncOutputs.addAll(asyncEvents)
            try {
                queue.addAll(processSyncEvents(key, syncEvents))
            } catch (e: CordaMessageAPIIntermittentException) {
                throw EventProcessorSyncEventsIntermittentException(processorStateUpdated, e)
            }
        }
        return Pair(processorStateUpdated, newAsyncOutputs)
    }

    private fun getNextEvent(
        queue: ArrayDeque<Record<K, E>>,
        consumerInputHash: String
    ): Record<K, E> {
        val event = queue.removeFirst()
        return event.copy(headers = event.headers.plus(Pair(INPUT_HASH_HEADER, consumerInputHash)))
    }

    private fun stateChangeAndOperation(
        state: State?,
        processed: State?
    ) = when {
        state == null && processed != null -> StateChangeAndOperation.Create(processed)
        state != null && processed != null -> StateChangeAndOperation.Update(processed)
        state != null && processed == null -> StateChangeAndOperation.Delete(state)
        else -> StateChangeAndOperation.Noop
    }


    private fun MutableMap<Record<K, E>, MutableList<MediatorMessage<Any>>>.addOutputs(
        inputEvent: Record<K, E>,
        asyncEvents: List<MediatorMessage<Any>>
    ) = computeIfAbsent(inputEvent) { mutableListOf() }.addAll(asyncEvents)

    /**
     * Send any synchronous events immediately and feed results back onto the queue.
     */
    private fun processSyncEvents(
        key: K,
        syncEvents: List<MediatorMessage<Any>>
    ): List<Record<K, E>> {
        return syncEvents.mapNotNull { message ->
            val destination = messageRouter.getDestination(message)

            @Suppress("UNCHECKED_CAST")
            val reply = with(destination) {
                message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                client.send(message) as MediatorMessage<E>?
            }
            reply?.let {
                addTraceContextToRecord(
                    Record(
                        "",
                        key,
                        reply.payload,
                        0,
                        listOf(Pair(SYNC_RESPONSE_HEADER, "true"))
                    ),
                    message.properties
                )
            }
        }
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
}