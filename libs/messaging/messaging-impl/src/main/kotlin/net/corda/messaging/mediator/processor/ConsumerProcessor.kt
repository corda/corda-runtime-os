package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorState
import net.corda.messaging.mediator.MessageBusConsumer
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.messaging.utils.toRecord
import net.corda.taskmanager.TaskManager
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

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
    private val mediatorState: MediatorState,
    private val eventProcessor: EventProcessor<K, S, E>
) {
    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private val metrics = EventMediatorMetrics(config.name)

    private val pollTimeout = config.pollTimeout

    private val stateManager = config.stateManager

    private var previousPollEmpty = true

    /**
     * Creates a message bus consumer and begins processing records from the subscribed topic.
     * @param consumerFactory used to create a message bus consumer
     * @param consumerConfig used to configure a consumer
     */
    fun processTopic(consumerFactory: MediatorConsumerFactory, consumerConfig: MediatorConsumerConfig<K, E>) {
        var attempts = 0
        var consumer: MediatorConsumer<K, E>? = null
        while (!mediatorState.stopped()) {
            attempts++
            try {
                if (consumer == null) {
                    consumer = consumerFactory.create(consumerConfig)
                    consumer.subscribe()
                }
                pollAndProcessEvents(consumer)
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

    /**
     * Polls records from the message bus, assigns records into groups, retrieves states for the records and then passses each group to
     * a thread for processing by the [eventProcessor].
     * Sends any async outputs back to the bus and saves states to the state manager.
     * Retries processing record keys whose states failed to save.
     * @param consumer message bus consumer
     */
    private fun pollAndProcessEvents(consumer: MediatorConsumer<K, E>) {
//        if (!previousPollEmpty) {
//            log.info("Polling")
//        }
        val messages = metrics.pollTimer.recordCallable {
            consumer.poll(pollTimeout)
        }!!
        previousPollEmpty = messages.isEmpty()
        if (messages.isNotEmpty()) {
            val startTimestamp = System.nanoTime()
            val polledRecords = messages.map { it.toRecord() }
//            logLag(messages)
            metrics.recordPollSize((consumer as MessageBusConsumer).topic, messages.size)
            var groups = groupAllocator.allocateGroups(polledRecords, config)
            var statesToProcess = stateManager.get(messages.map { it.key.toString() }.distinct())
            metrics.loadTimer.record(System.nanoTime() - startTimestamp, TimeUnit.NANOSECONDS)

            while (groups.isNotEmpty()) {
                // Process each group on a thread
                val groupStartTimestamp = System.nanoTime()
                val outputs = groups.filter {
                    it.isNotEmpty()
                }.map { group ->
                    taskManager.executeShortRunningTask {
                        eventProcessor.processEvents(group, statesToProcess)
                    }
                }.map {
                    it.join()
                }.fold(mapOf<K, EventProcessingOutput>()) { acc, cur ->
                    acc + cur
                }.mapKeys {
                    it.toString()
                }
                metrics.groupTimer.record(System.nanoTime() - groupStartTimestamp, TimeUnit.NANOSECONDS)

                // Persist state changes, send async outputs and setup to reprocess states that fail to persist
                val failedStates = processOutputs(outputs)
                statesToProcess = failedStates
                groups = assignNewGroupsForFailedStates(failedStates, polledRecords)
            }
            metrics.processorTimer.record(System.nanoTime() - startTimestamp, TimeUnit.NANOSECONDS)
            metrics.commitTimer.recordCallable {
                consumer.syncCommitOffsets()
            }
        }
    }

//    private fun logLag(messages: List<CordaConsumerRecord<K, E>>) {
//        val headers = messages.flatMap { it.headers }
//        val polledTime = headers
//            .firstOrNull { it.first == "polledTime" }
//            ?.second?.toLong()
//        val sentTimes = headers.filter { it.first == "sentTime" }.mapNotNull { it.second.toLong() }
//        val maxLag = if (sentTimes.isNotEmpty()) {
//            polledTime?.let { it - sentTimes.min() }
//        } else null
//        val minLag = if (sentTimes.isNotEmpty()) {
//            polledTime?.let { it - sentTimes.max() }
//        } else null
//        log.info("polledCount=${messages.size}, maxLag=$maxLag, minLag=$minLag")
//        if (maxLag != null) {
//            metrics.lagTimer.record(maxLag, TimeUnit.MILLISECONDS)
//        }
//    }

    /**
     * Persist any states outputted by the [eventProcessor] to the [stateManager]
     * Tracks failures, to allow for groups whose states failed to save to be retied.
     * Will send any asynchronous outputs back to the bus for states which saved successfully.
     * @return a map of all the states that failed to save by their keys.
     */
    private fun processOutputs(outputs: Map<String, EventProcessingOutput>): Map<String, State> {
        val persistStartTimestamp = System.nanoTime()
        val statesToCreate = mutableListOf<State>()
        val statesToUpdate = mutableListOf<State>()
        val statesToDelete = mutableListOf<State>()
        outputs.values.forEach {
            when (it.stateChangeAndOperation) {
                is StateChangeAndOperation.Create -> statesToCreate.add(it.stateChangeAndOperation.outputState)
                is StateChangeAndOperation.Update -> statesToUpdate.add(it.stateChangeAndOperation.outputState)
                is StateChangeAndOperation.Delete -> statesToDelete.add(it.stateChangeAndOperation.outputState)
                is StateChangeAndOperation.Noop -> {} // Do nothing.
            }
        }
        val failedToCreateKeys = stateManager.create(statesToCreate)
        val failedToCreate = stateManager.get(failedToCreateKeys)
        val failedToDelete = stateManager.delete(statesToDelete)
        val failedToUpdate = stateManager.update(statesToUpdate)
        val failedToUpdateOptimisticLockFailure = failedToUpdate.mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap()
        metrics.persistTimer.record(System.nanoTime() - persistStartTimestamp, TimeUnit.NANOSECONDS)
        val failedKeys = failedToCreate.keys + failedToDelete.keys + failedToUpdate.keys
        val outputsToSend = (outputs - failedKeys).values.flatMap { it.asyncOutputs }
        val sendStartTimestamp = System.nanoTime()
        sendAsynchronousEvents(outputsToSend)
        metrics.sendAsyncTimer.record(System.nanoTime() - sendStartTimestamp, TimeUnit.NANOSECONDS)

        return failedToCreate + failedToDelete + failedToUpdateOptimisticLockFailure
    }

    /**
     * Set processing groups for states that failed to save.
     */
    private fun assignNewGroupsForFailedStates(
        retrievedStates: Map<String, State>,
        polledEvents: List<Record<K, E>>
    ) = if (retrievedStates.isNotEmpty()) {
        groupAllocator.allocateGroups(polledEvents.filter { retrievedStates.containsKey(it.key.toString()) }, config)
    } else {
        listOf()
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
