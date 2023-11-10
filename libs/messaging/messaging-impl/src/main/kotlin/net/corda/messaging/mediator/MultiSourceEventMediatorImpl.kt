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

    private var clients = listOf<MessagingClient>()
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
        clients = mediatorComponentFactory.createClients(::onSerializationError)
        messageRouter = mediatorComponentFactory.createRouter(clients)
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        config.consumerFactories.map { consumerFactory ->
            taskManager.executeLongRunningTask {
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
                    } catch (exception: Exception) {
                        when (exception) {
                            is CordaMessageAPIIntermittentException -> {
                                attempts++
                                log.warn(
                                    "Multi-source event mediator ${config.name} failed to process records, " +
                                            "Retrying poll and process. Attempts: $attempts.")
                                consumer?.resetEventOffsetPosition()
                            }
                            else -> {
                                log.error(
                                    "${exception.message} Attempts: $attempts. Fatal error occurred!",
                                    exception
                                )
                                consumer?.close()
                                consumer = null
                            }
                        }
                    }
                }
                consumer
            }
        }.map {
            it.exceptionally { exception ->
                stop()
                lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${exception.message}")
                log.error(
                    "${exception.message}. Closing Multi-Source Event Mediator.",
                    exception
                )
                null
            }
        }.map {
            it.join()?.close()
        }
        clients.forEach { it.close() }
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        running.set(false)
    }

    private fun onSerializationError(event: ByteArray) {
        // TODO CORE-17012 Subscription error handling (DLQ)
        log.warn("Failed to deserialize event")
        log.debug { "Failed to deserialize event: ${event.contentToString()}" }
    }

    private fun pollAndProcessEvents(consumer: MediatorConsumer<K, E>) {
        val messages = consumer.poll(pollTimeout)
        val startTimestamp = System.nanoTime()
        if (messages.isNotEmpty()) {
            var groups = allocateGroups(messages.map { it.toRecord() })
            var states = stateManager.get(messages.map { it.key.toString() }.distinct())

            while (groups.isNotEmpty()) {
                val asynchronousOutputs = ConcurrentHashMap<String, MutableList<MediatorMessage<Any>>>()
                val statesToCreate = ConcurrentHashMap<String, State?>()
                val statesToUpdate = ConcurrentHashMap<String, State?>()
                val statesToDelete = ConcurrentHashMap<String, State?>()
                val flowEvents = ConcurrentHashMap<String, MutableList<Record<K, E>>>()

                // Process each group on a thread
                groups.filter {
                    it.isNotEmpty()
                }.map { group ->
                    taskManager.executeShortRunningTask {
                        // Process all same flow events in one go
                        group.map {
                            // Keep track of all records belonging to one flow
                            flowEvents.compute(it.key.toString()) { _, v ->
                                if (v == null) {
                                    it.value.toMutableList()
                                } else {
                                    v.addAll(it.value)
                                    v
                                }
                            }
                            val state = states.getOrDefault(it.key.toString(), null)
                            var processorState = stateManagerHelper.deserializeValue(state)?.let { stateValue ->
                                StateAndEventProcessor.State(
                                    stateValue,
                                    state?.metadata
                                )
                            }
                            val queue = ArrayDeque(it.value)
                            while (queue.isNotEmpty()) {
                                val event = queue.removeFirst()
                                val response = config.messageProcessor.onNext(processorState, event)
                                processorState = response.updatedState
                                processOutputEvents(it.key.toString(), response, asynchronousOutputs, queue, event)
                            }

                            // ---- Manage the state ----
                            val processedState = stateManagerHelper.createOrUpdateState(
                                it.key.toString(),
                                state,
                                processorState,
                            )

                            qualifyState(it.key.toString(), state, processedState, statesToCreate, statesToUpdate, statesToDelete)
                        }
                    }
                }.map {
                    it.join()
                }

                // Persist states changes
                val failedToCreateKeys = stateManager.create(statesToCreate.values.mapNotNull { it })
                val failedToCreate = stateManager.get(failedToCreateKeys.keys)
                val failedToDelete = stateManager.delete(statesToDelete.values.mapNotNull { it })
                val failedToUpdate = stateManager.update(statesToUpdate.values.mapNotNull { it })
                states = failedToCreate + failedToDelete + failedToUpdate
                groups = if (states.isNotEmpty()) {
                    allocateGroups(flowEvents.filterKeys { states.containsKey(it) }.values.flatten())
                } else {
                    listOf()
                }
                states.keys.forEach { asynchronousOutputs.remove(it) }
                sendAsynchronousEvents(asynchronousOutputs.values.flatten())
            }
            metrics.commitTimer.recordCallable {
                consumer.syncCommitOffsets()
            }
        }
        metrics.processorTimer.record(System.nanoTime() - startTimestamp, TimeUnit.NANOSECONDS)
    }

    /**
     * Decide, based on the original and processed state values, whether the state must be deleted, updated or
     * deleted; and add the relevant state value to the specific Map.
     */
    fun qualifyState(
        groupId: String,
        original: State?,
        processed: State?,
        toCreate : MutableMap<String, State?>,
        toUpdate : MutableMap<String, State?>,
        toDelete : MutableMap<String, State?>
    ) {
        // New state
        if (original == null && processed != null) {
            toCreate[groupId] = processed
        }

        // Update state
        if (original != null && processed != null) {
            toUpdate[groupId] = processed
        }

        // Delete state
        if (original != null && processed == null) {
            toDelete[groupId] = original
        }
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
     * Send any synchronous events immediately, add asynchronous events to the busEvents collection to be sent later
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

    private fun allocateGroups(events: List<Record<K, E>>): List<Map<K, List<Record<K, E>>>> {
        val groups = mutableListOf<MutableMap<K, List<Record<K, E>>>>()
        val groupCountBasedOnEvents = (events.size / 20).coerceAtLeast(1)
        val groupsCount = if (groupCountBasedOnEvents < config.threads) groupCountBasedOnEvents else config.threads
        for (i in 0 until groupsCount) {
            groups.add(mutableMapOf())
        }
        val buckets = events.groupBy { it.key }
        val bucketSizes = buckets.keys.sortedByDescending { buckets[it]?.size }
        for (i in buckets.size - 1 downTo 0 step 1) {
            val group = groups.minBy { it.values.flatten().size }
            val key = bucketSizes[i]
            val records = buckets[key]!!
            group[key] = records
        }

        return groups
    }
}