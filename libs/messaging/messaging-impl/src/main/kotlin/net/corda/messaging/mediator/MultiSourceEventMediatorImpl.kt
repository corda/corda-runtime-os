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
import java.util.UUID

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
    private val stateManagerHelper = StateManagerHelper<K, S, E>(
        stateManager, stateSerializer, stateDeserializer
    )
    private val taskManagerHelper = TaskManagerHelper(
        taskManager, stateManagerHelper
    )
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

    private fun stop() = Thread.currentThread().interrupt()

    private val stopped get() = Thread.currentThread().isInterrupted

    override fun close() {
        stop()
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
    }

    private fun onSerializationError(event: ByteArray) {
        log.debug { "Error serializing [$event] "}
        TODO("Not yet implemented")
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

    private fun processEvents() {
        log.debug { "Polling and processing events" }
        val messages = pollConsumers()
        if (messages.isNotEmpty()) {
            val msgGroups = messages.groupBy { it.key }
            val persistedStates = stateManager.get(msgGroups.keys.map { it.toString() })
            var msgProcessorTasks = taskManagerHelper.createMessageProcessorTasks(
                msgGroups, persistedStates, config.messageProcessor
            )
            do {
                val processingResults = taskManagerHelper.executeProcessorTasks(msgProcessorTasks)
                val conflictingStates = stateManagerHelper.persistStates(processingResults)
                val (successResults, failResults) = processingResults.partition {
                    !conflictingStates.contains(it.key.toString())
                }
                val clientTasks = taskManagerHelper.createClientTasks(successResults, messageRouter)
                val clientResults = taskManagerHelper.executeClientTasks(clientTasks)
                msgProcessorTasks =
                    taskManagerHelper.createMessageProcessorTasks(clientResults) +
                            taskManagerHelper.createMessageProcessorTasks(failResults, conflictingStates)
            } while (msgProcessorTasks.isNotEmpty())
            commitOffsets()
        }
    }

    private fun pollConsumers(): List<CordaConsumerRecord<K, E>> {
        return consumers.map { consumer ->
            taskManager.execute(TaskType.SHORT_RUNNING) {
                runBlocking {
                    consumer.poll(config.pollTimeout).await()
                }
            }
        }.map {
            it.join()
        }.flatten()
    }

    private fun commitOffsets() {
        consumers.map { consumer ->
            taskManager.execute(TaskType.SHORT_RUNNING) {
                runBlocking {
                    consumer.asyncCommitOffsets().await()
                }
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