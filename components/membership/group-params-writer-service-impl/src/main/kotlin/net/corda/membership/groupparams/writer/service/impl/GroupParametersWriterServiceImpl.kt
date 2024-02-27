package net.corda.membership.groupparams.writer.service.impl

import jdk.jshell.spi.ExecutionControl.NotImplementedException
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.toPersistentGroupParameters
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.GROUP_PARAMETERS_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Component(service = [GroupParametersWriterService::class])
class GroupParametersWriterServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
) : GroupParametersWriterService {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SERVICE = "GroupParametersWriterService"
        val idIndex = AtomicInteger()
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<GroupParametersWriterService>()
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private var impl: AtomicReference<InnerGroupParametersWriterService> = AtomicReference(InactiveImpl)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$SERVICE started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$SERVICE stopped.")
        coordinator.stop()
    }

    override fun put(recordKey: HoldingIdentity, recordValue: InternalGroupParameters) =
        impl.get().put(recordKey, recordValue)

    override fun remove(recordKey: HoldingIdentity) = impl.get().remove(recordKey)

    // for watching the dependencies
    private var dependencyHandle: RegistrationHandle? = null

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerGroupParametersWriterService : AutoCloseable {
        fun put(recordKey: HoldingIdentity, recordValue: InternalGroupParameters)

        fun remove(recordKey: HoldingIdentity)
    }

    private object InactiveImpl : InnerGroupParametersWriterService {
        override fun put(recordKey: HoldingIdentity, recordValue: InternalGroupParameters) =
            throw IllegalStateException("$SERVICE is currently inactive.")

        override fun remove(recordKey: HoldingIdentity) =
            throw IllegalStateException("$SERVICE is currently inactive.")

        override fun close() = Unit
    }

    private inner class ActiveImpl(
        private val publisher: Publisher
    ) : InnerGroupParametersWriterService {
        override fun put(recordKey: HoldingIdentity, recordValue: InternalGroupParameters) {
            publisher.publish(
                listOf(
                    Record(
                        GROUP_PARAMETERS_TOPIC,
                        recordKey.shortHash.toString(),
                        recordValue.toPersistentGroupParameters(recordKey, keyEncodingService)
                    )
                )
            ).forEach { it.get() }
        }

        override fun remove(recordKey: HoldingIdentity) =
            throw NotImplementedException("Removing group parameters is not supported.")

        override fun close() = publisher.close()
    }

    private fun activate(
        coordinator: LifecycleCoordinator,
        publisher: Publisher,
    ) {
        impl.getAndSet(
            ActiveImpl(publisher)
        ).close()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl
            .getAndSet(InactiveImpl)
            .close()
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
        dependencyHandle?.close()
        dependencyHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling stop event.")
        deactivate(coordinator)
        dependencyHandle?.close()
        dependencyHandle = null
        configHandle?.close()
        configHandle = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        logger.info("Handling registration changed event.")
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }

            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    private fun handleConfigChange(event: ConfigChangedEvent) {
        logger.info("Handling config changed event.")

        val publisher = publisherFactory.createPublisher(
            PublisherConfig("group-parameters-writer-service-${idIndex.incrementAndGet()}"),
            event.config.getConfig(MESSAGING_CONFIG)
        ).also {
            it.start()
        }
        activate(coordinator, publisher)
    }
}
