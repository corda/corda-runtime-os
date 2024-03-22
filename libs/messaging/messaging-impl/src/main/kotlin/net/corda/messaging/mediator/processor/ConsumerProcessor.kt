package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorSubscriptionState
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.messaging.utils.toRecord
import net.corda.taskmanager.TaskManager
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeoutException

/**
 * Class to construct a message bus consumer and begin processing its subscribed topic(s).
 * ConsumerProcessor will attempt to create message bus consumers and process records while the mediator is not stopped.
 * If any intermittent failures occur, the message bus consumer will reset to last committed position and retry poll and process loop.
 * If Fatal errors occur they will be throw back to the [MultiSourceEventMediatorImpl]
 * Polled records are divided into groups to process by the [groupAllocator].
 * Each group is processed on a different thread, submitted via the [taskManager].
 * An [eventProcessor] is used to process each group.
 * All asynchronous outputs (states and message bus events) are stored after all groups have been processed.
 * Any flows from groups that fail to save state to the [stateManager] are retried.
 */
@Suppress("LongParameterList")
class ConsumerProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val groupAllocator: GroupAllocator,
    private val taskManager: TaskManager,
    private val messageRouter: MessageRouter,
    private val mediatorSubscriptionState: MediatorSubscriptionState,
    private val eventProcessor: EventProcessor<K, S, E>,
    private val stateManagerHelper: StateManagerHelper<S>
) {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private val metrics = EventMediatorMetrics(config.name)

    private val pollTimeout = config.pollTimeout

    private val stateManager = config.stateManager

    companion object {
        private const val MAX_FAILURE_ATTEMPTS = 5
    }

    /**
     * Creates a message bus consumer and begins processing records from the subscribed topic.
     * @param consumerFactory used to create a message bus consumer
     * @param consumerConfig used to configure a consumer
     */
    fun processTopic(consumerFactory: MediatorConsumerFactory, consumerConfig: MediatorConsumerConfig<K, E>) {
        var attempts = 0
        var consumer: MediatorConsumer<K, E>? = null
        val failureCounts = mutableMapOf<String, Int>()
        while (!mediatorSubscriptionState.stopped()) {
            attempts++
            try {
                if (consumer == null) {
                    consumer = consumerFactory.create(consumerConfig)
                    consumer.subscribe()
                }
                pollLoop(consumer, failureCounts)
                attempts = 0
            } catch (exception: Exception) {
                val cause = if (exception is CompletionException) { exception.cause ?: exception} else exception
                when (cause) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "Multi-source event mediator ${config.name} failed to process records, " +
                                    "Retrying poll and process. Attempts: $attempts."
                        )
                        consumer?.resetEventOffsetPosition()
                    }
                    else -> {
                        log.debug { "${exception.message} Attempts: $attempts. Fatal error occurred!: $exception"}
                        consumer?.close()
                        //rethrow to break out of processing topic
                        throw cause
                    }
                }
            }
        }
        consumer?.close()
    }

    private fun pollLoop(consumer: MediatorConsumer<K, E>, failureCounts: MutableMap<String, Int>) {
        metrics.processorTimer.recordCallable {
            try {
                val inputs = getInputs(consumer)
                val outputs = processInputs(inputs)
                categorizeOutputs(outputs, failureCounts)
                commit(consumer, outputs, failureCounts)
            } catch (e: Exception) {
                log.warn("Retrying processing: ${e.message}.")
                consumer.resetEventOffsetPosition()
            }
        }
    }

    private fun getInputs(
        consumer: MediatorConsumer<K, E>,
    ): List<EventProcessingInput<K, E>> {
        val messages = consumer.poll(pollTimeout)
        val records = messages.map {
            it.toRecord()
        }.groupBy { it.key }
        val states = stateManager.get(records.keys.map { it.toString() })
        return generateInputs(states.values, records)
    }

    private fun processInputs(inputs: List<EventProcessingInput<K, E>>): Map<String, EventProcessingOutput> {
        val groups = groupAllocator.allocateGroups(inputs, config)
        return groups.filter {
            it.isNotEmpty()
        }.map { group ->
            val future = taskManager.executeShortRunningTask {
                eventProcessor.processEvents(group)
            }
            Pair(future, group)
        }.map { (future, group) ->
            try {
                future.getOrThrow(config.processorTimeout)
            } catch (e: TimeoutException) {
                metrics.consumerProcessorFailureCounter.increment(group.keys.size.toDouble())
                group.mapValues { (key, input) ->
                    val oldState = input.state
                    val state = stateManagerHelper.failStateProcessing(
                        key.toString(),
                        oldState,
                        "timeout occurred while processing events"
                    )
                    val stateChange = if (oldState != null) {
                        StateChangeAndOperation.Update(state)
                    } else {
                        StateChangeAndOperation.Create(state)
                    }
                    EventProcessingOutput(listOf(), stateChange)
                }
            }
        }.fold(mapOf<K, EventProcessingOutput>()) { acc, cur ->
            acc + cur
        }.mapKeys { (key, _) ->
            key.toString()
        }
    }

    private fun categorizeOutputs(
        outputs: Map<String, EventProcessingOutput>,
        failureCounts: MutableMap<String, Int>
    ) {
        val transients = outputs.filter {
            it.value.stateChangeAndOperation is StateChangeAndOperation.Transient
        }
        val failures = outputs.filter {
            it.value.stateChangeAndOperation.outputState?.metadata?.containsKey(PROCESSING_FAILURE) == true
        }
        failures.forEach { (key, _) ->
            failureCounts.compute(key) { _, value ->
                val currentValue = value ?: 0
                currentValue + 1
            }
        }
        val retryableFailures = failures.filter {
            (failureCounts[it.key] ?: 0) < MAX_FAILURE_ATTEMPTS
        }
        if (retryableFailures.isNotEmpty() || transients.isNotEmpty()) {
            throw CordaMessageAPIIntermittentException(
                "Retrying poll and process due to ${retryableFailures.size}/${transients.size} " +
                        "retryable failures/transient failures (out of ${outputs.size} total outputs)"
            )
        }
    }

    private fun commit(
        consumer: MediatorConsumer<K, E>,
        outputs: Map<String, EventProcessingOutput>,
        failureCounts: MutableMap<String, Int>
    ) {
        val (failed, toDelete) = processOutputs(outputs)
        if (failed.isNotEmpty()) {
            throw CordaMessageAPIIntermittentException(
                "Error occurred while writing states, retrying. ${failed.size} keys failed to write"
            )
        }
        metrics.commitTimer.recordCallable {
            consumer.syncCommitOffsets()
        }
        stateManager.delete(toDelete)
        outputs.forEach { (key, _) ->
            failureCounts.remove(key)
        }
    }

    /**
     * Generates inputs for a round of event processing.
     *
     * The input records must be the set of records that should be processed.
     */
    private fun generateInputs(states: Collection<State>, records: Map<K, List<Record<K, E>>>) : List<EventProcessingInput<K, E>> {
        val (runningStates, failedStates) = states.partition {
            it.metadata[PROCESSING_FAILURE] != true
        }
        if (failedStates.isNotEmpty()) {
            log.info("Not processing ${failedStates.size} states as processing has previously failed.")
        }
        val failedKeys = failedStates.map { it.key }.toSet()
        val recordsToProcess = records.filter { (key, _) ->
            !failedKeys.contains(key.toString())
        }
        val stateMap = runningStates.associateBy { it.key }
        return recordsToProcess.map { (key, value) ->
            EventProcessingInput(key, value, stateMap[key.toString()])
        }
    }

    /**
     * Persist any states outputted by the [eventProcessor] to the [stateManager]
     * Tracks failures, to allow for groups whose states failed to save to be retied.
     * Will send any asynchronous outputs back to the bus for states which saved successfully.
     * @return a map of all the states that failed to save by their keys.
     */
    private fun processOutputs(outputs: Map<String, EventProcessingOutput>): Pair<Map<String, State>, List<State>> {
        val statesToCreate = mutableListOf<State>()
        val statesToUpdate = mutableListOf<State>()
        val statesToDelete = mutableListOf<State>()
        outputs.values.forEach {
            when (it.stateChangeAndOperation) {
                is StateChangeAndOperation.Create -> statesToCreate.add(it.stateChangeAndOperation.outputState)
                is StateChangeAndOperation.Update -> statesToUpdate.add(it.stateChangeAndOperation.outputState)
                is StateChangeAndOperation.Delete -> statesToDelete.add(it.stateChangeAndOperation.outputState)
                else -> Unit // No state change required.
            }
        }
        val failedToCreateKeys = stateManager.create(statesToCreate)
        val failedToCreate = stateManager.get(failedToCreateKeys)
        val failedToUpdate = stateManager.update(statesToUpdate)
        val failedToUpdateOptimisticLockFailure = failedToUpdate.mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap()
        val failedKeys = failedToCreate.keys + failedToUpdate.keys
        val outputsToSend = (outputs - failedKeys).values.flatMap { it.asyncOutputs }
        sendAsynchronousEvents(outputsToSend)

        return Pair(failedToCreate + failedToUpdateOptimisticLockFailure, statesToDelete)
    }

    /**
     * Send asynchronous events to the message bus as directed by the [messageRouter]
     */
    private fun sendAsynchronousEvents(asyncOutputs: Collection<MediatorMessage<Any>>) {
        asyncOutputs.forEach { message ->
            with(messageRouter.getDestination(message)) {
                message.addProperty(net.corda.messaging.api.mediator.MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                client.send(message)
            }
        }
    }
}
