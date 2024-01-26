package net.corda.messaging.mediator.processor

import net.corda.data.messaging.mediator.MediatorState
import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.tracing.addTraceContextToRecord
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

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
    private val mediatorReplayService: MediatorReplayService,
) {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private val idempotentProcessor = config.idempotentProcessor

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
            val mediatorState = stateManagerHelper.deserializeMediatorState(inputState) ?: createNewMediatorState()
            val processorState = stateManagerHelper.deserializeValue(mediatorState)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    inputState?.metadata
                )
            }
            val (nonReplayConsumerInputs, replayOutputs) = getReplayOutputsAndNonReplayInputs(allConsumerInputs, mediatorState)
            if (nonReplayConsumerInputs.isEmpty()) {
                log.debug { "All records read in group with key ${input.key} are replayed records. Returning replays with no state update" }
                EventProcessingOutput(replayOutputs, StateChangeAndOperation.Noop)
            } else {
                processNewRecords(key, nonReplayConsumerInputs, processorState, mediatorState, inputState, replayOutputs)
            }
        }
    }

    private fun processNewRecords(
        key: K,
        nonReplayConsumerInputs: List<Record<K, E>>,
        inputProcessorState: StateAndEventProcessor.State<S>?,
        mediatorState: MediatorState,
        inputState: State?,
        replayOutputs: List<MediatorMessage<Any>>
    ): EventProcessingOutput {
        var processorState = inputProcessorState
        val asyncOutputs = mutableMapOf<ByteBuffer, MutableList<MediatorMessage<Any>>>()
        val processed = try {
            nonReplayConsumerInputs.forEach { consumerInputEvent ->
                val duplicate = asyncOutputs[mediatorReplayService.getInputHash(consumerInputEvent)]
                if (idempotentProcessor && duplicate != null) {
                    log.debug { "Duplicate input record within a single poll found with $key. Replaying outputs from previous invocation" }
                    asyncOutputs.addOutputs(consumerInputEvent, duplicate)
                } else {
                    val queue = ArrayDeque(listOf(consumerInputEvent))
                    while (queue.isNotEmpty()) {
                        val event = queue.removeFirst()
                        val response = config.messageProcessor.onNext(processorState, event)
                        processorState = response.updatedState
                        val (syncEvents, asyncEvents) = response.responseEvents.map { convertToMessage(it) }.partition {
                            messageRouter.getDestination(it).type == RoutingDestination.Type.SYNCHRONOUS
                        }
                        asyncOutputs.addOutputs(consumerInputEvent, asyncEvents)
                        queue.addAll(processSyncEvents(key, syncEvents))
                    }
                }
            }
            if (idempotentProcessor) {
                mediatorState.outputEvents = mediatorReplayService.getOutputEvents(mediatorState.outputEvents, asyncOutputs)
            }
            stateManagerHelper.createOrUpdateState(key.toString(), inputState, mediatorState, processorState)
        } catch (e: CordaMessageAPIIntermittentException) {
            // If an intermittent error occurs here, the RPC client has failed to deliver a message to another part
            // of the system despite the retry loop implemented there. This should trigger individual processing to
            // fail.
            asyncOutputs.clear()
            stateManagerHelper.failStateProcessing(
                key.toString(),
                inputState,
                "unable to contact Corda services while processing events"
            )
        }

        val stateChangeAndOperation = stateChangeAndOperation(inputState, processed)
        return EventProcessingOutput(replayOutputs + asyncOutputs.values.flatten(), stateChangeAndOperation)
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

    private fun getReplayOutputsAndNonReplayInputs(
        allConsumerInputs: List<Record<K, E>>,
        mediatorState: MediatorState
    ): Pair<List<Record<K, E>>, List<MediatorMessage<Any>>> {
        if (!idempotentProcessor) return Pair(allConsumerInputs, emptyList())

        val replaysByInput = mediatorReplayService.getReplayEvents(allConsumerInputs, mediatorState)
        if (replaysByInput.isEmpty()) return Pair(allConsumerInputs, emptyList())

        val nonReplayInputs = allConsumerInputs.toMutableList().also { inputs ->
            replaysByInput.map { it.inputRecord }.forEach { i -> inputs.remove(i) }
        }

        val outputs = replaysByInput.map { it.outputs }.flatten()
        return Pair(nonReplayInputs, outputs)
    }

    private fun MutableMap<ByteBuffer, MutableList<MediatorMessage<Any>>>.addOutputs(
        inputEvent: Record<K, E>,
        asyncEvents: List<MediatorMessage<Any>>
    ) = computeIfAbsent(mediatorReplayService.getInputHash(inputEvent)) { mutableListOf() }.addAll(asyncEvents)

    private fun createNewMediatorState(): MediatorState {
        return MediatorState.newBuilder()
            .setState(null)
            .setOutputEvents(mutableListOf())
            .build()
    }

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
                        reply.payload
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