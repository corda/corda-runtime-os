package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

@Suppress("LongParameterList")
class MultiSourceEventMediatorImpl<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    serializer: CordaAvroSerializer<Any>,
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
    private val stateManagerHelper = StateManagerHelper<K, S, E>(
        stateManager, serializer, stateDeserializer
    )
    private val taskManagerHelper = TaskManagerHelper(
        taskManager, stateManagerHelper
    )
    private val pollTimeoutInNanos = config.pollTimeout.toNanos()
    private val uniqueId = UUID.randomUUID().toString()
    private val lifecycleCoordinatorName = LifecycleCoordinatorName(
        "MultiSourceEventMediator--${config.name}", uniqueId
    )
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName) { _, _ -> }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting multi-source event mediator with config: $config" }
        lifecycleCoordinator.start()
        taskManager.execute(TaskType.LONG_RUNNING, ::run)
    }

    private fun stop() =
        Thread.currentThread().interrupt()

    private val stopped get() = Thread.currentThread().isInterrupted

    /**
     * This method is for closing the loop/thread externally. From inside the loop use the private [stopConsumeLoop].
     */
    override fun close() {
        lifecycleCoordinator.close()
    }

    private fun run() {
        var attempts = 0

        while (!stopped) {
            attempts++
            try {
                consumers = mediatorComponentFactory.createConsumers(::onSerializationError)
                clients = mediatorComponentFactory.createClients(::onSerializationError)
                messageRouter = mediatorComponentFactory.createRouter(clients)

                consumers.forEach { it.subscribe() }
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

                while (!stopped) {
                    processEventsWithRetries()
                }

            } catch (ex: Exception) {
                when (ex) {
                    is InterruptedException -> {
                        // Stopped
                    }

                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "${ex.message} Attempts: $attempts. Recreating consumers/clients and Retrying.", ex
                        )
                    }

                    else -> {
                        log.error(
                            "${ex.message} Attempts: $attempts. Closing subscription.", ex
                        )
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${ex.message}")
                        stop()
                    }
                }
            } finally {
                closeConsumersAndProducers()
            }
        }
        closeConsumersAndProducers()
    }

    private fun onSerializationError(event: ByteArray) {
        log.info("Error serializing [$event]")
        TODO()
    }

    private fun closeConsumersAndProducers() {
        consumers.forEach { it.close() }
        clients.forEach { it.close() }
    }

    private fun processEventsWithRetries() {
        var attempts = 0
        var keepProcessing = true
        while (keepProcessing && !stopped) {
            try {
                processEvents()
                keepProcessing = false
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handleProcessEventRetries(attempts, ex)
                    }

                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Multi-source event mediator ${config.name} failed to process messages, " +
                                    "Fatal error occurred.", ex
                        )
                    }
                }
            }
        }
    }

    private fun processEvents() {
        log.debug { "Polling and processing events" }
        val messages = poll(pollTimeoutInNanos)
        if (messages.isNotEmpty()) {
            val msgGroups = messages.groupBy { it.key }
            val persistedStates = stateManager.get(msgGroups.keys.map { it.toString() })
            var msgProcessorTasks = taskManagerHelper.createMsgProcessorTasks(
                msgGroups, persistedStates, config.messageProcessor
            )
            do {
                val processingResults = taskManagerHelper.executeProcessorTasks(msgProcessorTasks)
                val conflictingStates = stateManagerHelper.persistStates(processingResults)
                val (validResults, invalidResults) = processingResults.partition {
                    !conflictingStates.contains(it.key)
                }
                val clientTasks = taskManagerHelper.createProducerTasks(validResults, messageRouter)
                val clientResults = taskManagerHelper.executeProducerTasks(clientTasks)
                msgProcessorTasks =
                    taskManagerHelper.createMsgProcessorTasks(clientResults) +
                            taskManagerHelper.createMsgProcessorTasks(invalidResults, conflictingStates)
            } while (msgProcessorTasks.isNotEmpty())
            commitOffsets()
        }
    }

    private fun poll(pollTimeoutInNanos: Long): List<CordaConsumerRecord<K, E>> {
        val maxEndTime = System.nanoTime() + pollTimeoutInNanos
        return consumers.map { consumer ->
            val remainingTime = (maxEndTime - System.nanoTime()).coerceAtLeast(0)
            consumer.poll(Duration.ofNanos(remainingTime))
        }.flatten()
    }

    private fun commitOffsets() {
        runBlocking {
            consumers.map { consumer ->
                consumer.asyncCommitOffsets()
            }.map {
                it.await()
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
        ex: Exception
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
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }


//    private fun generateDeadLetterRecord(event: CordaConsumerRecord<K, E>, state: S?): Record<*, *> {
//        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(event.key))
//        val stateBytes =
//            if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(state)) else null
//        val eventValue = event.value
//        val eventBytes =
//            if (eventValue != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(eventValue)) else null
//        return Record(
//            Schemas.getDLQTopic(eventTopic), event.key,
//            StateAndEventDeadLetterRecord(clock.instant(), keyBytes, stateBytes, eventBytes)
//        )
//    }
}