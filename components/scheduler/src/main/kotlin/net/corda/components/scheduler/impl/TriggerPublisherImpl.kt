package net.corda.components.scheduler.impl

import net.corda.components.scheduler.TriggerPublisher
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [TriggerPublisher::class])
class TriggerPublisherImpl constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val publisherFactory: PublisherFactory,
    private val clock: () -> Instant = Instant::now
)  : TriggerPublisher, LifecycleEventHandler {
    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory
    ): this(coordinatorFactory, configurationReadService, publisherFactory, Instant::now)

    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        internal const val CLIENT_ID = "SCHEDULER_WRITER"
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<TriggerPublisher>()

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, this)
    private val lock = ReentrantLock()
    private var publisher: Publisher? = null
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun publish(taskName: String, topicName: String) {
        lock.withLock {
            logger.trace { "Publishing trigger for $taskName to $topicName" }
            publisher?.publish(listOf(
                Record(
                    topicName,
                    taskName,
                    ScheduledTaskTrigger(taskName, clock()))))
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    private fun onStopEvent() {
        registration?.close()
        registration = null

        configSubscription?.close()
        configSubscription = null

        closePublisher()
    }

    private fun onConfigChangedEvent(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return
        lock.withLock {
            publisher?.close()
            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), config)
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configSubscription =
                configurationReadService.registerComponentForUpdates(coordinator, setOf(ConfigKeys.MESSAGING_CONFIG))
        } else {
            coordinator.updateStatus(event.status)
            configSubscription?.close()
            configSubscription = null
            closePublisher()
        }
    }

    private fun closePublisher() = lock.withLock {
        publisher?.close()
        publisher = null
    }
}