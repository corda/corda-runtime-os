package net.corda.messaging.mediator

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.taskmanager.TaskManager
import net.corda.tracing.addTraceContextToRecord
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep

@Suppress("LongParameterList")
class MultiSourceEventMediatorImpl<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val taskManager: TaskManager,
    private val mediatorComponentFactory: MediatorComponentFactory<K, S, E>,
    private val lifecycleCoordinator: LifecycleCoordinator
) : MultiSourceEventMediator<K, S, E> {

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")
    private val mediatorState = MediatorState()
    private val consumerConfig = MediatorConsumerConfig(
        config.messageProcessor.keyClass,
        config.messageProcessor.eventValueClass,
        ::onSerializationError
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
        taskManager.executeLongRunningTask(::runMediator)
    }

    private fun runMediator() {
        mediatorState.running.set(true)
        val clients = mediatorComponentFactory.createClients(::onSerializationError)
        val messageRouter = mediatorComponentFactory.createRouter(clients)

        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

        config.consumerFactories.map { consumerFactory ->
            taskManager.executeLongRunningTask {
                val consumerProcessor = mediatorComponentFactory.createConsumerProcessor(config, taskManager, messageRouter, mediatorState)
                consumerProcessor.processTopic(consumerFactory, consumerConfig)
            }.exceptionally { exception ->
                handleTaskException(exception)
            }
        }.map {
            it.join()
        }

        clients.forEach { it.close() }
        mediatorState.running.set(false)
    }

    override fun close() {
        log.debug("Closing multi-source event mediator")
        mediatorState.stop()
        while (mediatorState.running()) {
            sleep(100)
        }
        lifecycleCoordinator.close()
    }

    private fun handleTaskException(exception: Throwable): Unit {
        mediatorState.stop()
        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${exception.message}")
        log.error("${exception.message}. Closing Multi-Source Event Mediator.", exception)
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
            var groups = groupAllocator.allocateGroups(messages.map { it.toRecord() }, config)
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
                val failedToCreate = stateManager.get(failedToCreateKeys)
                val failedToDelete = stateManager.delete(statesToDelete.values.mapNotNull { it })
                val failedToUpdate = stateManager.update(statesToUpdate.values.mapNotNull { it })
                val failedToUpdateOptimisticLockFailure = failedToUpdate.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
                val failedToUpdateStateDoesNotExist = (failedToUpdate - failedToUpdateOptimisticLockFailure).map { it.key }

                states = failedToCreate + failedToDelete + failedToUpdateOptimisticLockFailure

                groups = if (states.isNotEmpty()) {
                    groupAllocator.allocateGroups(flowEvents.filterKeys { states.containsKey(it) }.values.flatten(), config)
                } else {
                    listOf()
                }
                states.keys.forEach { asynchronousOutputs.remove(it) }
                failedToUpdateStateDoesNotExist.forEach { asynchronousOutputs.remove(it) }
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
                // Kafka
                busEvents.compute(key) { _, value ->
                    val list = value ?: mutableListOf()
                    list.add(message)
                    list
                }
            } else {
                // Http
                @Suppress("UNCHECKED_CAST")
                val reply = with(destination) {
                    message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                    client.send(message) as MediatorMessage<E>?
                }
                if (reply != null) {
                    // Convert reply into a record and added to the queue, so it can be processed later on
                    val replyHeaders = reply.properties.filter { (_, v) -> v is String }.map { (k, v) -> k to (v as String) }
                    @Suppress("UNCHECKED_CAST")
                    val record = addTraceContextToRecord(Record("", event.key, reply.payload), replyHeaders) as Record<K, E>
                    queue.addLast(record)
                }
            }
        }
    }

    private fun<K : Any, E: Any> MediatorMessage<E>.asRecord(key: K): Record<K, E>  {
        // Convert reply into a record and added to the queue, so it can be processed later on
        return Record("", key, payload)
    }
}
