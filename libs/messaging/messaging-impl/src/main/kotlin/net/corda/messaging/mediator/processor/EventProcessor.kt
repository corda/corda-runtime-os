package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
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
            val processed = stateManagerHelper.createOrUpdateState(groupKey, state, processorState)
            val stateUpdate = when {
                state == null && processed != null -> StateUpdate.Create(processed)
                state != null && processed != null -> StateUpdate.Update(processed)
                state != null && processed == null -> StateUpdate.Delete(state)
                else -> StateUpdate.Noop
            }

            EventProcessingOutput(asyncOutputs, stateUpdate)
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