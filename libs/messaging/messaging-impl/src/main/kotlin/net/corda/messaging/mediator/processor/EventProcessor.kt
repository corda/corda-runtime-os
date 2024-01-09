package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.State.Companion.VERSION_INITIAL_VALUE
import net.corda.messaging.api.constants.MessagingMetadataKeys.FAILED_STATE
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
    private val messageRouter: MessageRouter
) {

    /**
     * Process a group of events.
     * @param group Group of records of various keys
     * @param retrievedStates states for a group
     */
    fun processEvents(
        group: Map<K, List<Record<K, E>>>,
        retrievedStates: Map<String, State>
    ) : Map<K, EventProcessingOutput> {
        return group.mapValues { groupEntry ->
            val groupKey = groupEntry.key.toString()
            val state = retrievedStates.getOrDefault(groupKey, null)
            var processorState = stateManagerHelper.deserializeValue(state)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    state?.metadata
                )
            }
            val asyncOutputs = mutableListOf<MediatorMessage<Any>>()
            val queue = ArrayDeque(groupEntry.value)
            val processed = try {
                while (queue.isNotEmpty()) {
                    val event = queue.removeFirst()
                    val response = config.messageProcessor.onNext(processorState, event)
                    processorState = response.updatedState
                    val (syncEvents, asyncEvents) = response.responseEvents.map { convertToMessage(it) }.partition {
                        messageRouter.getDestination(it).type == RoutingDestination.Type.SYNCHRONOUS
                    }
                    asyncOutputs.addAll(asyncEvents)
                    val returnedMessages = processSyncEvents(groupEntry.key, syncEvents)
                    queue.addAll(returnedMessages)
                }
                stateManagerHelper.createOrUpdateState(groupKey, state, processorState)
            } catch (e: CordaMessageAPIIntermittentException) {
                // If an intermittent error occurs here, the RPC client has failed to deliver a message to another part
                // of the system despite the retry loop implemented there. This should trigger individual processing to
                // fail.
                val newMetadata = (state?.metadata?.toMutableMap() ?: mutableMapOf()).also {
                    it[FAILED_STATE] = true
                }
                asyncOutputs.clear()
                State(
                    groupKey,
                    byteArrayOf(),
                    version = state?.version ?: VERSION_INITIAL_VALUE,
                    metadata = Metadata(newMetadata)
                )
            }
            val stateChangeAndOperation = when {
                state == null && processed != null -> StateChangeAndOperation.Create(processed)
                state != null && processed != null -> StateChangeAndOperation.Update(processed)
                state != null && processed == null -> StateChangeAndOperation.Delete(state)
                else -> StateChangeAndOperation.Noop
            }

            EventProcessingOutput(asyncOutputs, stateChangeAndOperation)
        }
    }

    /**
     * Send any synchronous events immediately and feed results back onto the queue.
     */
    private fun processSyncEvents(
        key: K,
        syncEvents: List<MediatorMessage<Any>>
    ) : List<Record<K, E>> {
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