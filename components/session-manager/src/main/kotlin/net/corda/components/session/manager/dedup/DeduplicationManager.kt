package net.corda.components.session.manager.dedup

import com.typesafe.config.Config
import net.corda.data.session.RequestWindow
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class DeduplicationManager(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val configs: Map<String, Config>,
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()

        private const val SESSION_KEY = "SESSION"
        private const val BOOT_KEY = "BOOT"
        private const val MESSAGING_KEY = "MESSAGING"

        private const val INSTANCE_ID = "instance-id"
        private const val MAX_SESSION_LENGTH = "session.dedup.maxSessionLength"
        private const val DEDUP_STATE_TOPIC = "session.dedup.topic.state"
        private const val DEDUP_EVENT_TOPIC = "session.dedup.topic.event"
        private const val CLIENT_ID = "session.dedup.publisher.clientId"
        private const val CONSUMER_GROUP = "session.dedup.consumer.groupName"
    }

    private val coordinator = coordinatorFactory.createCoordinator<DeduplicationManager>(::eventHandler)
    private val scheduledTasks = mutableMapOf<String, ScheduledFuture<*>>()

    private var stateAndEventSub: StateAndEventSubscription<String, RequestWindow, Any>? = null
    private var publisher: Publisher? = null
    private var scheduledExecutorService: ScheduledExecutorService? = null

    private val messagingConfig = configs[MESSAGING_KEY] ?: throw CordaMessageAPIFatalException("Config not found")
    private val bootConfig = configs[BOOT_KEY] ?: throw CordaMessageAPIFatalException("Config not found")
    private val sessionConfig = configs[SESSION_KEY] ?: throw CordaMessageAPIFatalException("Config not found")

    private val instanceId = bootConfig.getInt(INSTANCE_ID)
    private val maxSessionLength = sessionConfig.getLong(MAX_SESSION_LENGTH)
    private val clientId = sessionConfig.getString(CLIENT_ID)
    private val stateTopic = sessionConfig.getString(DEDUP_STATE_TOPIC)
    private val eventTopic = sessionConfig.getString(DEDUP_EVENT_TOPIC)
    private val consumerGroup = sessionConfig.getString(CONSUMER_GROUP)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting deduplication manager." }
                val scheduledExecutorService = resetScheduledExecutorService()
                val publisher = resetPublisher()

                stateAndEventSub?.close()
                val dedupState = DedupState(maxSessionLength, stateTopic, publisher, scheduledExecutorService, scheduledTasks)
                stateAndEventSub = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(consumerGroup, eventTopic, instanceId),
                    DedupProcessor(dedupState),
                    configs[MESSAGING_KEY]!!,
                    DedupListener(dedupState)
                )
            }
            is StopEvent -> {
                logger.debug { "Stopping deduplication manager." }
                publisher?.close()
                stateAndEventSub?.close()
                scheduledExecutorService?.shutdownNow()
            }
        }
    }

    private fun resetPublisher(): Publisher {
        publisher?.close()
        val publisher = publisherFactory.createPublisher(PublisherConfig(clientId), messagingConfig)
        this.publisher = publisher
        return publisher
    }

    private fun resetScheduledExecutorService(): ScheduledExecutorService {
        scheduledExecutorService?.shutdownNow()
        val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        this.scheduledExecutorService = scheduledExecutorService
        return scheduledExecutorService
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
