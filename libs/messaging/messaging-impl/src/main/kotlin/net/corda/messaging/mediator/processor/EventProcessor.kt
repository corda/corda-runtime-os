package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.MediatorState
import net.corda.messaging.mediator.StateManagerHelper

/**
 * Class to process records received from the consumer
 */
class EventProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val stateManagerHelper: StateManagerHelper<K, S, E>,
    private val messageRouter: MessageRouter,
    private val mediatorState: MediatorState
) {
    
    fun processEvents(
        group: Map<K, List<Record<K, E>>>,
        retrievedStates: Map<String, State>,
    ) {
        group.map { groupEntry ->
            val groupKey = groupEntry.key.toString()
            val state = retrievedStates.getOrDefault(groupKey, null)
            var processorState = stateManagerHelper.deserializeValue(state)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    state?.metadata
                )
            }
            val queue = ArrayDeque(groupEntry.value)
            while (queue.isNotEmpty()) {
                val event = queue.removeFirst()
                val response = config.messageProcessor.onNext(processorState, event)
                processorState = response.updatedState
                processOutputEvents(groupKey, response, queue, event)
            }

            // ---- Manage the state ----
            qualifyState(groupKey, state, processorState)
        }
    }

    /**
     * Send any synchronous events immediately and feed results back onto the queue, add asynchronous events to the busEvents collection to
     * be sent later
     */
    private fun processOutputEvents(
        key: String,
        response: StateAndEventProcessor.Response<S>,
        queue: ArrayDeque<Record<K, E>>,
        event: Record<K, E>
    ) {
        val output = response.responseEvents.map { convertToMessage(it) }
        output.forEach { message ->
            val destination = messageRouter.getDestination(message)
            if (destination.type == RoutingDestination.Type.ASYNCHRONOUS) {
                mediatorState.asynchronousOutputs.compute(key) { _, value ->
                    val list = value ?: mutableListOf()
                    list.add(message)
                    list
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val reply = with(destination) {
                    message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                    client.send(message) as MediatorMessage<E>?
                }
                if (reply != null) {
                    queue.addLast(
                        Record(
                            "",
                            event.key,
                            reply.payload,
                        )
                    )
                }
            }
        }
    }

    /**
     * Decide, based on the original and processed state values, whether the state must be deleted, updated or
     * deleted; and add the relevant state value to the specific Map.
     */
    private fun qualifyState(
        groupKey: String,
        original: State?,
        processorState: StateAndEventProcessor.State<S>?,
    ) {
        val processed = stateManagerHelper.createOrUpdateState(groupKey, original, processorState)
        mediatorState.statesToPersist.apply {
            when {
                original == null && processed != null -> statesToCreate[groupKey] = processed
                original != null && processed != null -> statesToUpdate[groupKey] = processed
                original != null && processed == null -> statesToDelete[groupKey] = original
            }
        }
    }

    private fun convertToMessage(record: Record<*, *>): MediatorMessage<Any> {
        return MediatorMessage(
            record.value!!,
            record.headers.toMessageProperties().also { it[MessagingClient.MSG_PROP_KEY] = record.key },
        )
    }

    private fun List<Pair<String, String>>.toMessageProperties() =
        associateTo(mutableMapOf()) { (key, value) -> key to (value as Any) }
}