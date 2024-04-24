package net.corda.messaging.mediator

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.messaging.mediator.processor.CachingStateManagerWrapper
import net.corda.taskmanager.TaskManager
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
    private val mediatorSubscriptionState = MediatorSubscriptionState()
    private val consumerConfig = MediatorConsumerConfig(
        config.messageProcessor.keyClass,
        config.messageProcessor.eventValueClass,
        ::onSerializationError
    )

    override fun start() {
        log.debug { "Starting multi-source event mediator with config: $config" }
        lifecycleCoordinator.start()
        taskManager.executeLongRunningTask(::runMediator)
    }

    private fun runMediator() {
        mediatorSubscriptionState.running.set(true)
        val clients = mediatorComponentFactory.createClients(::onSerializationError)
        val messageRouter = mediatorComponentFactory.createRouter(clients)

        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

        val stateManagerWrapper = CachingStateManagerWrapper(config.stateManager)

        config.consumerFactories.map { consumerFactory ->
            taskManager.executeLongRunningTask {
                val consumerProcessor =
                    mediatorComponentFactory.createConsumerProcessor(
                        config.copy(name = config.name + "." + consumerFactory.topicName),
                        taskManager,
                        messageRouter,
                        mediatorSubscriptionState,
                        stateManagerWrapper
                    )
                consumerProcessor.processTopic(consumerFactory, consumerConfig)
            }.exceptionally { exception ->
                handleTaskException(exception)
            }
        }.map {
            it.join()
        }

        clients.forEach { it.close() }
        mediatorSubscriptionState.running.set(false)
    }

    override fun close() {
        log.debug("Closing multi-source event mediator")
        mediatorSubscriptionState.stop()
        while (mediatorSubscriptionState.running()) {
            sleep(100)
        }
        lifecycleCoordinator.close()
    }

    private fun handleTaskException(exception: Throwable): Unit {
        mediatorSubscriptionState.stop()
        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${exception.message}")
        log.error("${exception.message}. Closing Multi-Source Event Mediator.", exception)
    }

    private fun onSerializationError(event: ByteArray) {
        // TODO CORE-17012 Subscription error handling (DLQ)
        log.warn("Failed to deserialize event")
        log.debug { "Failed to deserialize event: ${event.contentToString()}" }
    }
}