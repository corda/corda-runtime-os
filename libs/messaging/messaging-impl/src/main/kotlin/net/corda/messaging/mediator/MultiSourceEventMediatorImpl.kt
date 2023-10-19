package net.corda.messaging.mediator

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
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.taskmanager.TaskManager
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
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

    private val consumerWorkers = mutableListOf<CompletableFuture<Unit>>()
    private val stopped = AtomicBoolean(false)

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
        consumerWorkers.map { it.join() }
        lifecycleCoordinator.close()
    }

    private fun run() {
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        consumerWorkers.addAll(
            config.consumerFactories.map { consumerFactory ->
                ConsumerWorker(consumerFactory)
            }.map { taskManager.executeLongRunningTask(it::run) }
        )
    }

    private inner class ConsumerWorker(
        private val consumerFactory: MediatorConsumerFactory
    ) {
        private var consumer: MediatorConsumer<K, E>? = null
        fun run() {
            var attempts = 0

            while (!stopped()) {
                attempts++
                try {
                    consumer = mediatorComponentFactory.createConsumer(consumerFactory, ::onSerializationError)
                    clients = mediatorComponentFactory.createClients(::onSerializationError)
                    messageRouter = mediatorComponentFactory.createRouter(clients)

                    consumer!!.subscribe()

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
                    closeConsumerAndProducers()
                }
            }
        }

        private fun closeConsumerAndProducers() {
            consumer!!.close()
            clients.forEach { it.close() }
        }

        private fun processEventsWithRetries() {
            var attempts = 0
            var keepProcessing = true
            while (keepProcessing && !stopped()) {
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
            val messages = pollConsumer()
            if (stopped()) {
                return
            }
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

        private fun pollConsumer(): List<CordaConsumerRecord<K, E>> {
            return metrics.pollTimer.recordCallable {
                consumer!!.poll(config.pollTimeout)
            }!!
        }

        private fun commitOffsets() {
            metrics.commitTimer.recordCallable {
                consumer!!.syncCommitOffsets()
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
                consumer!!.resetEventOffsetPosition()
            } else {
                val message = "Multi-source event mediator ${config.name} failed to process records, " +
                        "Attempts: $attempts. Max reties exceeded."
                log.warn(message, exception)
                throw CordaMessageAPIIntermittentException(message, exception)
            }
        }
    }

    private fun onSerializationError(event: ByteArray) {
        // TODO CORE-17012 Subscription error handling (DLQ)
        log.warn("Failed to deserialize event")
        log.debug { "Failed to deserialize event: ${event.contentToString()}" }
    }
}