package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorSubscriptionState
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.taskmanager.TaskManager
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.Queue
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
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
@Suppress("LongParameterList", "TooManyFunctions")
class ConsumerProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val groupAllocator: GroupAllocator,
    private val taskManager: TaskManager,
    private val messageRouter: MessageRouter,
    private val mediatorSubscriptionState: MediatorSubscriptionState,
    private val eventProcessor: EventProcessor<K, S, E>,
    private val stateManager: CachingStateManagerWrapper
) {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private val metrics = EventMediatorMetrics(config.name)

    private val pollTimeout = config.pollTimeout

    companion object {
        private const val MAX_FAILURE_ATTEMPTS = 5
        private const val TOPIC_OFFSET_METADATA_PREFIX = "topic.offset"
        private const val DELETE_LATER_METADATA_PROPERTY = "delete.later"
        private const val PRIORITY_METADATA_PROPERTY = "priority"
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
        val deleteLater = mutableMapOf<String, State>()
        val outputsToProcess =
            LinkedBlockingDeque<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>()
        val inputsToCommit = LinkedBlockingDeque<List<CordaConsumerRecord<K, E>>>()
        val inputsToRetry = LinkedBlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>()
        val assignedPartitions: MutableSet<CordaTopicPartition> = ConcurrentHashMap.newKeySet()
        val rebalanceListener = object : CordaConsumerRebalanceListener {
            override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
                assignedPartitions.removeAll(partitions)
            }

            override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
                assignedPartitions.addAll(partitions)
            }
        }
        while (!mediatorSubscriptionState.stopped()) {
            attempts++
            try {
                if (consumer == null) {
                    consumer = consumerFactory.create(consumerConfig, rebalanceListener)
                    deleteLater.clear()
                    consumer.subscribe()
                    taskManager.executeLongRunningTask {
                        while (!mediatorSubscriptionState.stopped()) {
                            writeReadyOutputs(
                                consumer,
                                outputsToProcess,
                                failureCounts,
                                deleteLater,
                                inputsToRetry,
                                inputsToCommit
                            )
                        }
                    }
                }
                pollLoop(consumer, outputsToProcess, inputsToCommit, inputsToRetry, assignedPartitions)
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
                        log.debug { "${exception.message} Attempts: $attempts. Fatal error occurred!: $exception" }
                        consumer?.close()
                        //rethrow to break out of processing topic
                        throw cause
                    }
                }
            }
        }
        consumer?.close()
    }

    private fun writeReadyOutputs(
        consumer: MediatorConsumer<K, E>,
        outputsToProcess: BlockingDeque<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>,
        failureCounts: MutableMap<String, Int>,
        deleteLater: MutableMap<String, State>,
        inputsToRetry: BlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>,
        inputsToCommit: BlockingDeque<List<CordaConsumerRecord<K, E>>>
    ) {
        val outputs = outputsToProcess.pollAll(100, TimeUnit.MILLISECONDS)
        if (outputs.isNotEmpty()) {
            try {
                val (successfulOutputs, categorizeDelayedActions) = categorizeOutputs(
                    outputs,
                    failureCounts,
                    inputsToRetry
                )
                commitProducerOutputs(
                    consumer,
                    successfulOutputs,
                    failureCounts,
                    deleteLater,
                    inputsToRetry,
                    categorizeDelayedActions,
                    inputsToCommit
                )
            } catch (e: Exception) {
                log.warn("Error trying to commit produced outputs", e)
                outputsToProcess.putBackAll(outputs)
            }
        }
    }

    /**
     * Execute a single pass of the poll/process loop.
     *
     * Failure counts of processing per-key are provided from the outer loop as context for each pass of this function.
     * When processing outputs, if an error is encountered that is not known to be transient when processing a key, the
     * failure count is incremented by 1. If the count exceeds the MAX_FAILURE_COUNT value, then processing is marked as
     * failed for this key.
     *
     * On any transient failure, or failure that has not exceeded MAX_FAILURE_COUNT, the poll position is reset and
     * another attempt is made to process the input events. For transient failures, this causes events to be retried
     * until the transient failure clears. For non-transient failures, processing will give up after a fixed number of
     * attempts.
     *
     * @param consumer The consumer to retrieve input events from
     * @param failureCounts A map of keys to number of failures for that key. Used to pass context between invocations
     *                      of this function.
     */
    private fun pollLoop(
        consumer: MediatorConsumer<K, E>,
        outputsToProcess: Queue<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>,
        inputsToCommit: BlockingDeque<List<CordaConsumerRecord<K, E>>>,
        inputsToRetry: BlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>,
        assignedPartitions: MutableSet<CordaTopicPartition>
    ) {
        metrics.processorTimer.recordCallable {
            try {
                val (inputs, retryInputs) = getInputs(consumer, inputsToRetry, assignedPartitions)
                val retriedOutputs = processInputs(emptyList(), retryInputs, inputsToCommit)
                transferOutputs(retriedOutputs, outputsToProcess)
                val outputs = processInputs(inputs, emptyList(), inputsToCommit)
                transferOutputs(outputs, outputsToProcess)
                // TODO try and commit offsets async
                commitConsumerOffsets(consumer, inputsToCommit)
            } catch (e: Exception) {
                log.warn("Retrying poll processing: ${e.message}.")
                consumer.resetEventOffsetPosition()
            }
        }
    }

    private fun commitConsumerOffsets(
        consumer: MediatorConsumer<K, E>,
        inputsToCommit: BlockingDeque<List<CordaConsumerRecord<K, E>>>
    ) {
        val readyToCommit = inputsToCommit.drainAll()
        if (readyToCommit.isNotEmpty()) {
            val allReadyToCommit = readyToCommit.flatMap { it }
            try {
                metrics.commitTimer.recordCallable {
                    consumer.syncCommitOffsets(allReadyToCommit)
                }
            } catch (e: Exception) {
                // Failure to commit not a big deal, but need to process the records again
                inputsToCommit.putBackAll(listOf(allReadyToCommit))
            }
        }
    }

    private fun transferOutputs(
        outputs: Map<String, Pair<CompletableFuture<EventProcessingOutput<K, E>>, CompletableFuture<Unit>>>,
        outputsToProcess: Queue<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>
    ) {
        for (output in outputs) {
            output.value.first.whenComplete { outputToProcess: EventProcessingOutput<K, E>?, throwable: Throwable? ->
                if (outputToProcess != null) {
                    outputsToProcess.add(output.key to (outputToProcess to output.value.second))
                } else {
                    log.error("Unexpected throwable from step", throwable)
                }
            }
        }
    }

    private fun <T> BlockingQueue<T>.drainAll(): List<T> {
        val buffer = mutableListOf<T>()
        this.drainTo(buffer)
        return buffer
    }

    private fun <T> BlockingQueue<T>.pollAll(timeout: Long, unit: TimeUnit): List<T> {
        val buffer = mutableListOf<T>()
        val first = this.poll(timeout, unit)
        if (first != null) {
            buffer.add(first)
            this.drainTo(buffer)
        }
        return buffer
    }

    private fun <T> BlockingDeque<T>.putBackAll(all: List<T>) {
        all.reversed().forEach {
            this.addFirst(it)
        }
    }

    /**
     * Retrieve a set of input events and states.
     *
     * Input events are grouped by key and associated with the corresponding state from the state manager. Any states
     * that are marked as failed will not have the corresponding events processed.
     *
     * @param consumer The consumer to use to retrieve input events.
     * @return The set of inputs for processing.
     */
    private fun getInputs(
        consumer: MediatorConsumer<K, E>,
        inputsToRetry: BlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>,
        assignedPartitions: Set<CordaTopicPartition>,
    ): Pair<List<EventProcessingInput<K, E>>, List<Pair<EventProcessingInput<K, E>, CompletableFuture<Unit>>>> {
        // Retries filtered to exclude any partitions we don't own
        val retryMessages = inputsToRetry.drainAll()
            .map { it.first.filter { CordaTopicPartition(it.topic, it.partition) in assignedPartitions } to it.second }
            .filter { it.first.isNotEmpty() }
        val polledMessages =
            if (retryMessages.isNotEmpty()) consumer.poll(Duration.ZERO) else consumer.poll(pollTimeout)
        val newRecords = polledMessages.groupBy { it.key }
        val retryRecords = retryMessages.groupBy { it.first.first().key }
        val states = stateManager.get(newRecords.map { it.toString() }, retryRecords.map { it.toString() }).values
        return generateInputs(states, newRecords) to regenerateInputs(states, retryMessages)
    }

    private fun CordaConsumerRecord<K, E>.metadataKey(): String {
        return "$TOPIC_OFFSET_METADATA_PREFIX.${topic}.${partition}"
    }

    private fun String.toTopicAndPartition(): Pair<String, Int>? {
        return if (startsWith(TOPIC_OFFSET_METADATA_PREFIX)) {
            val topicAndPartition = substring(TOPIC_OFFSET_METADATA_PREFIX.length + 1)
            return topicAndPartition.substringBeforeLast('.') to topicAndPartition.substringAfterLast('.').toInt()
        } else null
    }

    /**
     * Process a list of inputs into outputs.
     *
     * This function groups inputs into roughly even sizes (by event count), and then passes each group to a new thread
     * for processing. The corresponding outputs are collected together for further categorization and commit.
     *
     * If processing times out, the state will be marked as failed. This will prevent further processing for events on
     * that key if processing has been attempted MAX_FAILURE_ATTEMPTS times without success.
     *
     * @param inputs The set of input to process.
     * @return The set of outputs for further processing.
     */
    private fun processInputs(
        inputs: List<EventProcessingInput<K, E>>,
        retryInputs: List<Pair<EventProcessingInput<K, E>, CompletableFuture<Unit>>>,
        inputsToCommit: BlockingDeque<List<CordaConsumerRecord<K, E>>>
    ): Map<String, Pair<CompletableFuture<EventProcessingOutput<K, E>>, CompletableFuture<Unit>>> {
        val prunedInputs = inputs.map { input ->
            input.state?.let { state ->
                val (notSeenRecords, seenRecords) = input.records.partition { record ->
                    // Because original design expects to replay last message to re-trigger outputs stored in state
                    // we use >= rather than >
                    record.offset >= ((state.metadata[record.metadataKey()] as? Number)?.toLong() ?: -1)
                }
                inputsToCommit.add(seenRecords)
                input.copy(records = notSeenRecords)
            } ?: input
        }
        val (alreadySeenInputs, newInputs) = prunedInputs.partition { input ->
            input.records.isEmpty()
        }
        return (retryInputs.map { (input, _) ->
            val persistFuture = CompletableFuture<Unit>()
            val future = taskManager.executeShortRunningTask(
                input.key,
                input.state?.metadata?.get(PRIORITY_METADATA_PROPERTY) as? Long ?: 0,
                persistFuture,
                true
            ) {
                val updatedInput =
                    EventProcessingInput(
                        input.key,
                        input.records,
                        stateManager.get(setOf(input.key.toString())).values.firstOrNull()
                    )
                val output = eventProcessor.processEvents(mapOf(input.key to updatedInput)).values.first()
                output
            }
            input.key to (future to persistFuture)
        } + newInputs.map { input ->
            val persistFuture = CompletableFuture<Unit>()
            val future = taskManager.executeShortRunningTask(
                input.key,
                input.state?.metadata?.get(PRIORITY_METADATA_PROPERTY) as? Long ?: 0,
                persistFuture,
                false
            ) {
                val updatedInput =
                    EventProcessingInput(
                        input.key,
                        input.records,
                        stateManager.get(setOf(input.key.toString())).values.firstOrNull()
                    )
                val output = eventProcessor.processEvents(mapOf(input.key to updatedInput)).values.first()
                output
            }
            input.key to (future to persistFuture)
        } + alreadySeenInputs.map { input ->
            val persistFuture = CompletableFuture<Unit>()
            val result: EventProcessingOutput<K, E> =
                if (input.state?.metadata?.containsKey(DELETE_LATER_METADATA_PROPERTY) ?: false) {
                    EventProcessingOutput<K, E>(
                        listOf(), StateChangeAndOperation.Delete(input.state!!), emptyList()
                    )
                } else {
                    EventProcessingOutput<K, E>(listOf(), StateChangeAndOperation.Noop(input.state), emptyList())
                }
            input.key to (CompletableFuture.completedFuture(result) to persistFuture)
        }).toMap().mapKeys { (key, _) ->
            key.toString()
        }
    }

    /**
     * Categorizes transient and non-transient failures from processing the batch.
     *
     * Transient failures are marked as such and should cause a StateChangeAndOperation.Transient to be returned for
     * that key from processing. Non-transient failures are anything else that results in failure, and are marked in the
     * state metadata.
     *
     * Note that "non-transient" failures are just those that are not identified to be transient, and so may clear up on
     * retry if they are incorrectly categorized. It is important that anything marked as transient is so 100% of the
     * time however. As such this retry logic is a balance between failing anything that is a true failure and allowing
     * some non-identified transient failures to be corrected.
     *
     * If any transient failures, or any non-transient failures that haven't been retried MAX_FAILURE_ATTEMPTS times,
     * are identified, this function will throw an error signalling that processing should be retried from the last poll
     * position.
     *
     * @param outputs The outputs from this round of processing.
     * @param failureCounts The current failure counts for all keys that have not subsequently succeeded.
     * @throws CordaMessageAPIIntermittentException if any retryable errors are identified.
     */
    private fun categorizeOutputs(
        outputs: List<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>,
        failureCounts: MutableMap<String, Int>,
        inputsToRetry: BlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>
    ): Pair<List<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>, List<Runnable>> {
        val transients = outputs.filter {
            it.second.first.stateChangeAndOperation is StateChangeAndOperation.Transient
        }
        val failures = outputs.filter {
            it.second.first.stateChangeAndOperation.outputState?.metadata?.containsKey(PROCESSING_FAILURE) == true
        }
        failures.forEach { (key, _) ->
            failureCounts.compute(key) { _, value ->
                val currentValue = value ?: 0
                currentValue + 1
            }
        }
        val retryableFailures = failures.filter {
            (failureCounts[it.first] ?: 0) < MAX_FAILURE_ATTEMPTS
        }
        val nonFatalFailures = (retryableFailures + transients).toMap()
        val failureDelayedActions = nonFatalFailures.map { failure ->
            Runnable {
                inputsToRetry.add(failure.value.first.processedOffsets to failure.value.second)
            }
        }
        return outputs.filter {
            it.first !in nonFatalFailures
        } to failureDelayedActions
    }

    /**
     * Commits output states, publishes asynchronous records, and updates the consumer sync position.
     *
     * A failure while committing state creates and updates, or while publishing output events, will result in a retry
     * occurring. This may result in duplicate messages being sent. Deletes are not subject to this and are done
     * best-effort.
     *
     * @param consumer The consumer to commit the offsets for
     * @param outputs The outputs to commit back
     * @param failureCounts Context from previous runs. This is cleared for each key present in the outputs on
     *                      successful commit.
     */
    private fun commitProducerOutputs(
        consumer: MediatorConsumer<K, E>,
        outputs: List<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>,
        failureCounts: MutableMap<String, Int>,
        previousDeleteLater: MutableMap<String, State>,
        inputsToRetry: BlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>,
        categorizeDelayedActions: List<Runnable>,
        inputsToCommit: BlockingDeque<List<CordaConsumerRecord<K, E>>>
    ) {
        val (toDelete, processOutputsDelayedActions) = processOutputs(
            outputs,
            inputsToRetry,
            inputsToCommit
        )
        // Delete after committing offsets to satisfy flow engine replay requirements.
        val (safeToDelete, deleteLater) = toDelete.partition { state ->
            isStateSafeToDelete(consumer, state)
        }
        val (previousNowSafeToDelete, _) = previousDeleteLater.values.partition { state ->
            isStateSafeToDelete(consumer, state)
        }
        stateManager.delete(safeToDelete + previousNowSafeToDelete)
        val toDeleteLater = deleteLater.map {
            it.copy(metadata = Metadata(it.metadata + mapOf(DELETE_LATER_METADATA_PROPERTY to true)))
        }
        stateManager.update(toDeleteLater) // Updates seen offsets to prevent re-processing
        for (delayedAction in (categorizeDelayedActions + processOutputsDelayedActions)) {
            delayedAction.run()
        }
        previousNowSafeToDelete.forEach { previousDeleteLater.remove(it.key) }
        previousDeleteLater.putAll(deleteLater.map { it.key to it.copy(version = it.version + 1) }.toMap())
        outputs.forEach { (key, _) ->
            failureCounts.remove(key)
        }
    }

    private fun isStateSafeToDelete(consumer: MediatorConsumer<K, E>, state: State): Boolean {
        return state.metadata.entries.all {
            it.key.toTopicAndPartition()?.let { (topic, partition) ->
                consumer.alreadySyncedOffset(topic, partition, (it.value as Number).toLong()) ?: true
            } ?: true
        }
    }

    /**
     * Generates inputs for a round of event processing.
     *
     * The input records must be the set of records that should be processed.
     */
    private fun generateInputs(
        states: Collection<State>,
        records: Map<K, List<CordaConsumerRecord<K, E>>>
    ): List<EventProcessingInput<K, E>> {
        if (records.isEmpty()) return emptyList()
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

    private fun regenerateInputs(
        states: Collection<State>,
        retryMessages: List<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>
    ): List<Pair<EventProcessingInput<K, E>, CompletableFuture<Unit>>> {
        if (retryMessages.isEmpty()) return emptyList()
        val (runningStates, failedStates) = states.partition {
            it.metadata[PROCESSING_FAILURE] != true
        }
        if (failedStates.isNotEmpty()) {
            log.info("Not processing ${failedStates.size} states as processing has previously failed.")
        }
        val failedKeys = failedStates.map { it.key }.toSet()
        val records = retryMessages.associateBy { it.first.first().key }
        val recordsToProcess = records.filter { (key, _) ->
            !failedKeys.contains(key.toString())
        }
        val stateMap = runningStates.associateBy { it.key }
        return recordsToProcess.map { (key, value) ->
            EventProcessingInput(key, value.first, stateMap[key.toString()]) to value.second
        }
    }

    /**
     * Persist any states outputted by the [eventProcessor] to the [stateManager]
     * Tracks failures, to allow for groups whose states failed to save to be retied.
     * Will send any asynchronous outputs back to the bus for states which saved successfully.
     * @return a map of all the states that failed to save by their keys.
     */
    private fun processOutputs(
        outputs: List<Pair<String, Pair<EventProcessingOutput<K, E>, CompletableFuture<Unit>>>>,
        inputsToRetry: BlockingQueue<Pair<List<CordaConsumerRecord<K, E>>, CompletableFuture<Unit>>>,
        inputsToCommit: BlockingDeque<List<CordaConsumerRecord<K, E>>>
    ): Pair<List<State>, List<Runnable>> {
        val statesToCreate = mutableListOf<State>()
        val statesToUpdate = mutableListOf<State>()
        val statesToDelete = mutableListOf<State>()
        val outputsMap = outputs.toMap()
        val writeFutures = mutableMapOf<String, CompletableFuture<Unit>>()
        outputsMap.forEach {
            val (output, writeFuture) = it.value
            val stateWithOffsets = output.stateChangeAndOperation.outputState?.let {
                it.copy(metadata = calculateMaxOffsets(it.metadata, output.processedOffsets))
            }
            when (output.stateChangeAndOperation) {
                is StateChangeAndOperation.Create -> statesToCreate.add(stateWithOffsets!!)
                is StateChangeAndOperation.Update -> statesToUpdate.add(stateWithOffsets!!)
                is StateChangeAndOperation.Delete -> statesToDelete.add(stateWithOffsets!!)
                else -> Unit // No state change required.
            }
            writeFutures[it.key] = writeFuture
        }
        val failedToCreateKeys = stateManager.create(statesToCreate)
        val failedToCreate = stateManager.get(failedToCreateKeys)
        val failedToUpdate = stateManager.update(statesToUpdate)
        val failedToUpdateOptimisticLockFailure = failedToUpdate.mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap()
        val failedKeys = failedToCreate.keys + failedToUpdate.keys
        val successful = outputsMap - failedKeys
        val outputsToSend = successful.values.flatMap { it.first.asyncOutputs }
        sendAsynchronousEvents(outputsToSend)
        val successfulDelayedActions = successful.map {
            Runnable {
                writeFutures[it.key]!!.complete(Unit)
                inputsToCommit.add(it.value.first.processedOffsets)
            }
        }
        val unsuccessfulStates = failedToCreate + failedToUpdateOptimisticLockFailure
        val unsuccessfulDelayedActions = unsuccessfulStates.map { unsuccessful ->
            Runnable {
                val output = outputsMap[unsuccessful.key]!!
                inputsToRetry.add(output.first.processedOffsets to output.second)
            }
        }
        return statesToDelete to (successfulDelayedActions + unsuccessfulDelayedActions)
    }

    private fun calculateMaxOffsets(
        existingMetadata: Metadata,
        processedOffsets: List<CordaConsumerRecord<K, E>>
    ): Metadata {
        val result = existingMetadata.toMutableMap()
        processedOffsets.forEach {
            result.compute(it.metadataKey()) { _, value ->
                if (value == null || value !is Number) {
                    it.offset
                } else {
                    maxOf(value.toLong(), it.offset)
                }
            }
        }
        result.putIfAbsent(PRIORITY_METADATA_PROPERTY, Instant.now().toEpochMilli())
        return Metadata(result)
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
