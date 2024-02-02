package net.corda.messaging.mediator.processor

import net.corda.data.messaging.mediator.MediatorState
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
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
import java.util.concurrent.TimeUnit

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
    /**
     * Process a group of events.
     * @param group Group of records of various keys
     * @param retrievedStates states for a group
     */
    fun processEvents(
        inputs: Map<K, EventProcessingInput<K, E>>,
        topic: String,
        metrics: EventMediatorMetrics
    ): Map<K, EventProcessingOutput> {
        val startTime = System.nanoTime()
        return inputs.mapValues { (key, input) ->
            val groupKey = key.toString()
            val mediatorState = stateManagerHelper.deserializeMediatorState(input.state) ?: createNewMediatorState()
            var processorState = stateManagerHelper.deserializeValue(mediatorState)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    input.state?.metadata
                )
            }
            metrics.timer(topic, "GROUP_STATE_TIME").record(startTime, TimeUnit.NANOSECONDS)
            val asyncOutputs = mutableMapOf<Record<K, E>, MutableList<MediatorMessage<Any>>>()
            val allConsumerInputs = input.records
            var processedCount = 0
            var rpcCount = 0
            var procTime = 0L
            var rpcTime = 0L
            var sortTime = 0L
            var rpc = false
            val processed = try {
                metrics.recordSize(topic, "GROUP_PROC_RECORDS", allConsumerInputs.size)
                allConsumerInputs.forEach { consumerInputEvent ->
                    val queue = ArrayDeque(listOf(consumerInputEvent))
                    while (queue.isNotEmpty()) {
                        processedCount++
                        val event = queue.removeFirst()

                        val procStartTime = System.nanoTime()
                        val response = config.messageProcessor.onNext(processorState, event)
                        val t1 = System.nanoTime()
                        procTime += t1 - procStartTime

                        processorState = response.updatedState
                        val (syncEvents, asyncEvents) = response.responseEvents.map { convertToMessage(it) }.partition {
                            messageRouter.getDestination(it).type == RoutingDestination.Type.SYNCHRONOUS
                        }
                        asyncOutputs.computeIfAbsent(consumerInputEvent) { mutableListOf() }.addAll(asyncEvents)
                        val t2 = System.nanoTime()
                        sortTime += t2 - t1

                        val returnedMessages = processSyncEvents(key, syncEvents)
                        rpcTime += System.nanoTime() - t2

                        rpc = rpc || syncEvents.isNotEmpty()
                        queue.addAll(returnedMessages)
                        rpcCount += returnedMessages.size
                    }
                    //metrics.recordSize(topic, "GROUP_PROC_COUNT", processedCount)
                    //metrics.recordSize(topic, "GROUP_RPC_COUNT", rpcCount)
                }
                metrics.timer(topic, "GROUP_PROC_TIME").record(procTime, TimeUnit.NANOSECONDS)
                metrics.timer(topic, "GROUP_SORT_TIME").record(procTime, TimeUnit.NANOSECONDS)
                metrics.timer(topic, "GROUP_RPC_TIME").record(rpcTime, TimeUnit.NANOSECONDS)
                if (rpc) {
                    metrics.timer(topic, "GROUP_RPC_ONLY_TIME").record(rpcTime, TimeUnit.NANOSECONDS)
                }
                mediatorState.outputEvents = mediatorReplayService.getOutputEvents(mediatorState.outputEvents, asyncOutputs)
                stateManagerHelper.createOrUpdateState(groupKey, input.state, mediatorState, processorState)
            } catch (e: CordaMessageAPIIntermittentException) {
                // If an intermittent error occurs here, the RPC client has failed to deliver a message to another part
                // of the system despite the retry loop implemented there. This should trigger individual processing to
                // fail.
                asyncOutputs.clear()
                stateManagerHelper.failStateProcessing(
                    groupKey,
                    input.state,
                    "unable to contact Corda services while processing events"
                )
            }

            val stateChangeAndOperation = when {
                input.state == null && processed != null -> StateChangeAndOperation.Create(processed)
                input.state != null && processed != null -> StateChangeAndOperation.Update(processed)
                input.state != null && processed == null -> StateChangeAndOperation.Delete(input.state)
                else -> StateChangeAndOperation.Noop
            }
            metrics.timer(topic, "GROUP_TOTAL_TIME").record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)

            EventProcessingOutput(asyncOutputs.values.flatten(), stateChangeAndOperation)
        }
    }

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