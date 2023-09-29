package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.UUID

// TODO This will be implemented with CORE-15754
@Suppress("LongParameterList", "unused_parameter")
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
    private val uniqueId = UUID.randomUUID().toString()
    private val lifecycleCoordinatorName = LifecycleCoordinatorName(
        "MultiSourceEventMediator--${config.name}", uniqueId
    )
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName) { _, _ -> }

    override val subscriptionName: LifecycleCoordinatorName
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    private fun stop() = Thread.currentThread().interrupt()

    private val stopped get() = Thread.currentThread().isInterrupted

    override fun close() {
        stop()
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
                    processEvents()
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
        TODO("Not yet implemented")
    }

    private fun closeConsumersAndProducers() {
        consumers.forEach { it.close() }
        clients.forEach { it.close() }
    }

    private fun processEvents() {
        log.debug { "Polling and processing events" }
        val messages = pollConsumers()
        if (messages.isNotEmpty()) {
            // TODO Process messages
            commitOffsets()
        }
    }

    private fun pollConsumers(): List<CordaConsumerRecord<K, E>> {
        return runBlocking {
            consumers.map { consumer ->
                consumer.poll(config.pollTimeout)
            }.map {
                it.await()
            }
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
}