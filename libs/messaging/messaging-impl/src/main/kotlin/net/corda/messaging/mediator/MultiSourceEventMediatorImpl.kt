package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.*
import net.corda.messaging.api.mediator.config.EventMediatorConfig
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

    private var consumers = listOf<MediatorConsumer<K, E>>()
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
        var attempts = 0

        while (!stopped()) {
            attempts++
            try {
                consumers = mediatorComponentFactory.createConsumers(::onSerializationError)
                clients = mediatorComponentFactory.createClients(::onSerializationError)
                messageRouter = mediatorComponentFactory.createRouter(clients)

                consumers.forEach { it.subscribe() }
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

                while (!stopped()) {
                    processEventsWithRetries()
                }

            } catch (exception: Exception) {
                when (exception) {
                    is InterruptedException -> {
                        log.info("Multi-Source Event Mediator is stopped. Closing consumers and clients.")
                    }

                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "${exception.message} Attempts: $attempts. Recreating consumers and clients and retrying.",
                            exception
                        )
                    }

                    else -> {
                        log.error(
                            "${exception.message} Attempts: $attempts. Closing Multi-Source Event Mediator.", exception
                        )
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${exception.message}")
                        stop()
                    }
                }
            } finally {
                closeConsumersAndProducers()
            }
        }
        running.set(false)
    }

    private fun onSerializationError(event: ByteArray) {
        // TODO CORE-17012 Subscription error handling (DLQ)
        log.warn("Failed to deserialize event")
        log.debug { "Failed to deserialize event: ${event.contentToString()}" }
    }

    private fun closeConsumersAndProducers() {
        consumers.forEach { it.close() }
        clients.forEach { it.close() }
    }

    private fun processEventsWithRetries() {
        var attempts = 0
        var keepProcessing = true
        while (keepProcessing && !stopped()) {
            try {
                pollAndProcessEvents()
                keepProcessing = false
            } catch (exception: Exception) {
                when (exception) {
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handleProcessEventRetries(attempts, exception)
                    }

                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Multi-source event mediator ${config.name} failed to process messages, " +
                                    "Fatal error occurred.", exception
                        )
                    }
                }
            }
        }
    }

//    private fun processEvents() {
//        val messages = pollConsumers()
//        if (stopped()) {
//            return
//        }
//        if (messages.isNotEmpty()) {
//            val msgGroups = messages.groupBy { it.key }
//            val persistedStates = stateManager.get(msgGroups.keys.map { it.toString() })
//            var msgProcessorTasks = taskManagerHelper.createMessageProcessorTasks(
//                msgGroups, persistedStates, config.messageProcessor
//            )
//            do {
//                val processingResults = taskManagerHelper.executeProcessorTasks(msgProcessorTasks)
//                val conflictingStates = stateManagerHelper.persistStates(processingResults)
//                val (successResults, failResults) = processingResults.partition {
//                    !conflictingStates.contains(it.key.toString())
//                }
//                val clientTasks = taskManagerHelper.createClientTasks(successResults, messageRouter)
//                val clientResults = taskManagerHelper.executeClientTasks(clientTasks)
//                msgProcessorTasks =
//                    taskManagerHelper.createMessageProcessorTasks(clientResults) +
//                            taskManagerHelper.createMessageProcessorTasks(failResults, conflictingStates)
//            } while (msgProcessorTasks.isNotEmpty())
//            commitOffsets()
//        }
//    }

    private fun pollAndProcessEvents() {
        val messages = pollConsumers()
        if (messages.isNotEmpty()) {
            var groups = allocateGroups(messages.map { it.toRecord() })
            var states = stateManager.get(messages.map { it.key.toString() })
            while (groups.isNotEmpty()) {
                val updateStates = mutableMapOf<String, State?>()
                val deleteStates = mutableMapOf<String, State?>()
                val flowEvents = mutableMapOf<String, MutableList<Record<K, E>>>()
                // Process each group on a thread
                groups.map { group ->
                    taskManager.executeShortRunningTask {
                        // Process all same flow events in one go
                        group.map { it ->
                            flowEvents.compute(it.key.toString()) { _, v ->
                                if (v == null) {
                                    it.value.toMutableList()
                                } else {
                                    v.addAll(it.value)
                                    v
                                }
                            }
                            var state = states[it.key.toString()]
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
                                val output = response.responseEvents.map { taskManagerHelper.convertToMessage(it) }
                                output.forEach { message ->
                                    val destination = messageRouter.getDestination(message)
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
                            // Update states
                            val newState = stateManagerHelper.createOrUpdateState(
                                it.key.toString(),
                                state,
                                processorState,
                            )
                            if (newState == null) {
                                deleteStates[it.key.toString()] = states[it.key.toString()]
                            } else {
                                val incrementVersion = if (state == null) 0 else 1
                                val updatedState = newState.copy(version = newState.version + incrementVersion)
                                updateStates[it.key.toString()] = updatedState
                            }
                        }
                    }
                }.map {
                    it.join()
                }
                // Delete states
                val failedToDelete = stateManager.delete(deleteStates.values.mapNotNull { it })
                val failedToUpdate = stateManager.update(updateStates.values.mapNotNull { it })
                states = failedToDelete + failedToUpdate
                groups = if (states.isNotEmpty()) {
                    allocateGroups(flowEvents.filterKeys { states.containsKey(it) }.values.flatten())
                } else {
                    listOf()
                }
            }
        }
        commitOffsets()
    }

    private fun allocateGroups(events: List<Record<K, E>>): List<Map<K, List<Record<K, E>>>> {
        val groups = mutableListOf<MutableMap<K, List<Record<K, E>>>>()
        for (i in 0 until config.threads) {
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

    private fun pollConsumers(): List<CordaConsumerRecord<K, E>> {
        return metrics.pollTimer.recordCallable {
            consumers.map { consumer ->
                consumer.poll(Duration.ofMillis(20))
            }.flatten()
        }!!
    }

    private fun commitOffsets() {
        metrics.commitTimer.recordCallable {
            consumers.map { consumer ->
                consumer.syncCommitOffsets()
            }
        }
    }

    /**
     * Handle retries for event processing.
     * Reset [MediatorConsumer]s position and retry poll and process of event records
     * Retry a max of [EventMediatorConfig.processorRetries] times.
     * If [EventMediatorConfig.processorRetries] is exceeded then throw a [CordaMessageAPIIntermittentException]
     */
    private fun handleProcessEventRetries(
        attempts: Int,
        exception: Exception,
    ) {
        if (attempts <= config.processorRetries) {
            log.warn(
                "Multi-source event mediator ${config.name} failed to process records, " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            consumers.forEach { it.resetEventOffsetPosition() }
        } else {
            val message = "Multi-source event mediator ${config.name} failed to process records, " +
                    "Attempts: $attempts. Max reties exceeded."
            log.warn(message, exception)
            throw CordaMessageAPIIntermittentException(message, exception)
        }
    }
}