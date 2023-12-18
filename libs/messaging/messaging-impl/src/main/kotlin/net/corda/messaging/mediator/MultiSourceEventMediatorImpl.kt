package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.messaging.utils.toRecord
import net.corda.taskmanager.TaskManager
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LongParameterList")
class MultiSourceEventMediatorImpl<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    stateSerializer: CordaAvroSerializer<S>,
    stateDeserializer: CordaAvroDeserializer<S>,
    private val stateManager: StateManager,
    private val taskManager: TaskManager,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : MultiSourceEventMediator<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private lateinit var messageRouter: MessageRouter
    private val mediatorComponentFactory = MediatorComponentFactory(
        config.messageProcessor, config.consumerFactories, config.clientFactories, config.messageRouterFactory
    )
    private val metrics = EventMediatorMetrics(config.name)
    private val stateManagerHelper = StateManagerHelper<K, S, E>(
        stateManager, stateSerializer, stateDeserializer
    )
    private val taskManagerHelper = TaskManagerHelper(
        taskManager, stateManagerHelper, metrics
    )
    private val groupAllocator = GroupAllocator()
    private val uniqueId = UUID.randomUUID().toString()
    private val lifecycleCoordinatorName = LifecycleCoordinatorName(
        "MultiSourceEventMediator--${config.name}", uniqueId
    )
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName) { _, _ -> }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinatorName

    private val stopped = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    // TODO This timeout was set with CORE-17768 (changing configuration value would affect other messaging patterns)
    //  This should be reverted to use configuration value once event mediator polling is refactored (planned for 5.2)
    private val pollTimeout = Duration.ofMillis(50)

    override fun start() {
        log.debug { "Starting multi-source event mediator with config: $config" }
        lifecycleCoordinator.start()
        taskManager.executeLongRunningTask(::run)
    }

    private fun stop() = stopped.set(true)

    private fun stopped() = stopped.get()

    override fun close() {
        log.debug("Closing multi-source event mediator")
        stop()
        while (running.get()) {
            sleep(100)
        }
        lifecycleCoordinator.close()
    }

    private fun run() {
        running.set(true)
        val clients = mediatorComponentFactory.createClients(::onSerializationError)
        messageRouter = mediatorComponentFactory.createRouter(clients)
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

        config.consumerFactories.map { consumerFactory ->
            taskManager.executeLongRunningTask {
                processTopic(consumerFactory)
            }.exceptionally { exception ->
                handleTaskException(exception)
            }
        }.map {
            it.join()
        }

        clients.forEach { it.close() }
        if (lifecycleCoordinator.status != LifecycleStatus.ERROR) {
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
        running.set(false)
    }

    private fun processTopic(consumerFactory: MediatorConsumerFactory) {
        var attempts = 0
        var consumer: MediatorConsumer<K, E>? = null
        while (!stopped()) {
            attempts++
            try {
                if (consumer == null) {
                    consumer = consumerFactory.create(
                        MediatorConsumerConfig(
                            config.messageProcessor.keyClass,
                            config.messageProcessor.eventValueClass,
                            ::onSerializationError
                        )
                    )
                    consumer.subscribe()
                }
                pollAndProcessEvents(consumer)
                attempts = 0
            } catch (exception: Exception) {
                val cause = if (exception is CompletionException ) { exception.cause ?: exception} else exception
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
    }

    private fun handleTaskException(exception: Throwable): Nothing? {
        stop()
        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${exception.message}")
        log.error(
            "${exception.message}. Closing Multi-Source Event Mediator.",
            exception
        )
        return null
    }

    private fun onSerializationError(event: ByteArray) {
        // TODO CORE-17012 Subscription error handling (DLQ)
        log.warn("Failed to deserialize event")
        log.debug { "Failed to deserialize event: ${event.contentToString()}" }
    }

    private fun pollAndProcessEvents(consumer: MediatorConsumer<K, E>) {
        val messages = consumer.poll(pollTimeout)
        val startTimestamp = System.nanoTime()
        val polledRecords = messages.map { it.toRecord() }
        if (messages.isNotEmpty()) {
            var groups = groupAllocator.allocateGroups(polledRecords, config)
            var statesToProcess = stateManager.get(messages.map { it.key.toString() }.distinct())

            while (groups.isNotEmpty()) {
                val asynchronousOutputs = ConcurrentHashMap<String, MutableList<MediatorMessage<Any>>>()
                val statesToPersist = StatestoPersist()

                // Process each group on a thread
                groups.filter {
                    it.isNotEmpty()
                }.map { group ->
                    taskManager.executeShortRunningTask {
                        processEventsInGroup(group, statesToProcess, asynchronousOutputs, statesToPersist)
                    }
                }.map {
                    it.join()
                }

                // Persist state changes, send async outputs and setup to reprocess states that fail to persist
                val failedStates = persistStatesAndRetrieveFailures(statesToPersist, asynchronousOutputs)
                statesToProcess = failedStates
                groups = assignNewGroupsForFailedStates(failedStates, polledRecords)
                sendAsynchronousEvents(asynchronousOutputs.values.flatten())
            }
            metrics.commitTimer.recordCallable {
                consumer.syncCommitOffsets()
            }
        }
        metrics.processorTimer.record(System.nanoTime() - startTimestamp, TimeUnit.NANOSECONDS)
    }

    private fun processEventsInGroup(
        group: Map<K, List<Record<K, E>>>,
        retrievedStates: Map<String, State>,
        asynchronousOutputs: ConcurrentHashMap<String, MutableList<MediatorMessage<Any>>>,
        statesToPersist: StatestoPersist
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
                processOutputEvents(groupKey, response, asynchronousOutputs, queue, event)
            }

            // ---- Manage the state ----
            qualifyState(groupKey, state, processorState, statesToPersist)
        }
    }

    /**
     * Decide, based on the original and processed state values, whether the state must be deleted, updated or
     * deleted; and add the relevant state value to the specific Map.
     */
    fun qualifyState(
        groupKey: String,
        original: State?,
        processorState: StateAndEventProcessor.State<S>?,
        statesToPersist: StatestoPersist
    ) {
        val processed = stateManagerHelper.createOrUpdateState(groupKey, original, processorState)
        statesToPersist.apply {
            when {
                original == null && processed != null -> statesToCreate[groupKey] = processed
                original != null && processed != null -> statesToUpdate[groupKey] = processed
                original != null && processed == null -> statesToPersist.statesToDelete[groupKey] = original
            }
        }
    }

    private fun persistStatesAndRetrieveFailures(
        statesToPersist: StatestoPersist,
        asynchronousOutputs: ConcurrentHashMap<String, MutableList<MediatorMessage<Any>>>
    ): Map<String, State> {
        val failedToCreateKeys = stateManager.create(statesToPersist.statesToCreate.values.mapNotNull { it })
        val failedToCreate = stateManager.get(failedToCreateKeys)
        val failedToDelete = stateManager.delete(statesToPersist.statesToDelete.values.mapNotNull { it })
        val failedToUpdate = stateManager.update(statesToPersist.statesToUpdate.values.mapNotNull { it })
        val failedToUpdateOptimisticLockFailure = failedToUpdate.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        val failedToUpdateStateDoesNotExist = (failedToUpdate - failedToUpdateOptimisticLockFailure).map { it.key }
        failedToUpdateStateDoesNotExist.forEach { asynchronousOutputs.remove(it) }

        val failedStates = failedToCreate + failedToDelete + failedToUpdateOptimisticLockFailure
        failedStates.keys.forEach { asynchronousOutputs.remove(it) }
        return failedStates
    }

    private fun assignNewGroupsForFailedStates(
        retrievedStates: Map<String, State>,
        polledEvents: List<Record<K, E>>
    ) = if (retrievedStates.isNotEmpty()) {
        groupAllocator.allocateGroups(polledEvents.filter { retrievedStates.containsKey(it.key.toString()) }, config)
    } else {
        listOf()
    }

    private fun sendAsynchronousEvents(busEvents: Collection<MediatorMessage<Any>>) {
        busEvents.forEach { message ->
            with(messageRouter.getDestination(message)) {
                message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                client.send(message)
            }
        }
    }

    /**
     * Send any synchronous events immediately and feed results back onto the queue, add asynchronous events to the busEvents collection to
     * be sent later
     */
    private fun processOutputEvents(
        key: String,
        response: StateAndEventProcessor.Response<S>,
        busEvents: MutableMap<String, MutableList<MediatorMessage<Any>>>,
        queue: ArrayDeque<Record<K, E>>,
        event: Record<K, E>
    ) {
        val output = response.responseEvents.map { taskManagerHelper.convertToMessage(it) }
        output.forEach { message ->
            val destination = messageRouter.getDestination(message)
            if (destination.type == RoutingDestination.Type.ASYNCHRONOUS) {
                busEvents.compute(key) { _, value ->
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

    data class StatestoPersist(
        val statesToCreate: ConcurrentHashMap<String, State?> = ConcurrentHashMap<String, State?>(),
        val statesToUpdate: ConcurrentHashMap<String, State?> = ConcurrentHashMap<String, State?>(),
        val statesToDelete: ConcurrentHashMap<String, State?> = ConcurrentHashMap<String, State?>(),
    )
}