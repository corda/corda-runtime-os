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
import net.corda.messaging.mediator.ConsumerProcessorState
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorState
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.messaging.utils.toRecord
import net.corda.taskmanager.TaskManager
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

/**
 * Class to construct a message bus consumer and begin processing its subscribed topic(s).
 * ConsumerProcessor will attempt to create message bus consumers and process records while the mediator is not stopped.
 * If any intermittent failures occur, the message bus consumer will reset to last committed position and retry poll and process loop.
 * If Fatal errors occur they will be throw back to the [MultiSourceEventMediatorImpl]
 * Polled records are divided into groups to process by the [groupAllocator].
 * Each group is processed on a different thread, submitted via the [taskManager].
 * An [eventProcessor] is used to process each group. This will update the [consumerProcessorState] with any outputs
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
    private val consumerProcessorState: ConsumerProcessorState,
    private val eventProcessor: EventProcessor<K, S, E>
) {
    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private val metrics = EventMediatorMetrics(config.name)

    // TODO This timeout was set with CORE-17768 (changing configuration value would affect other messaging patterns)
    //  This should be reverted to use configuration value once event mediator polling is refactored (planned for 5.2)
    private val pollTimeout = Duration.ofMillis(50)

    private val stateManager = config.stateManager

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
        val messages = consumer.poll(pollTimeout)
        val startTimestamp = System.nanoTime()
        val polledRecords = messages.map { it.toRecord() }
        if (messages.isNotEmpty()) {
            var groups = groupAllocator.allocateGroups(polledRecords, config)
            var statesToProcess = stateManager.get(messages.map { it.key.toString() }.distinct())

            while (groups.isNotEmpty()) {
                // Process each group on a thread
                groups.filter {
                    it.isNotEmpty()
                }.map { group ->
                    taskManager.executeShortRunningTask {
                        eventProcessor.processEvents(group, statesToProcess)
                    }
                }.map {
                    it.join()
                }

                // Persist state changes, send async outputs and setup to reprocess states that fail to persist
                val failedStates = persistStatesAndRetrieveFailures()
                statesToProcess = failedStates
                groups = assignNewGroupsForFailedStates(failedStates, polledRecords)
                sendAsynchronousEvents(consumerProcessorState.asynchronousOutputs.values.flatten())
                consumerProcessorState.asynchronousOutputs.clear()
            }
            metrics.commitTimer.recordCallable {
                consumer.syncCommitOffsets()
            }
        }
        metrics.processorTimer.record(System.nanoTime() - startTimestamp, TimeUnit.NANOSECONDS)
    }

    /**
     * Persist any states outputted by the [eventProcessor] to the [stateManager]
     * Tracks failures, to allow for groups whose states failed to save to be retied.
     * Will send any asynchronous outputs back to the bus for states which saved successfully.
     * @return a map of all the states that failed to save by their keys.
     */
    private fun persistStatesAndRetrieveFailures(): Map<String, State> {
        val asynchronousOutputs = consumerProcessorState.asynchronousOutputs
        val statesToPersist = consumerProcessorState.statesToPersist
        val failedToCreateKeys = stateManager.create(statesToPersist.statesToCreate.values.mapNotNull { it })
        val failedToCreate = stateManager.get(failedToCreateKeys)
        val failedToDelete = stateManager.delete(statesToPersist.statesToDelete.values.mapNotNull { it })
        val failedToUpdate = stateManager.update(statesToPersist.statesToUpdate.values.mapNotNull { it })
        statesToPersist.clear()
        val failedToUpdateOptimisticLockFailure = failedToUpdate.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        val failedToUpdateStateDoesNotExist = (failedToUpdate - failedToUpdateOptimisticLockFailure).map { it.key }
        failedToUpdateStateDoesNotExist.forEach { asynchronousOutputs.remove(it) }

        val failedStates = failedToCreate + failedToDelete + failedToUpdateOptimisticLockFailure
        failedStates.keys.forEach { asynchronousOutputs.remove(it) }
        return failedStates
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
