package net.corda.membership.impl.read.reader

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.impl.read.subscription.GroupParametersProcessor
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.read.GroupParametersReaderService
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.Schemas.Membership.GROUP_PARAMETERS_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.stream.Stream
import net.corda.data.membership.GroupParameters as GroupParametersAvro


@Component(service = [GroupParametersReaderService::class])
class GroupParametersReaderServiceImpl internal constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val subscriptionFactory: SubscriptionFactory,
    private val groupParametersFactory: GroupParametersFactory,
    private val groupParametersCache: MemberDataCache<GroupParameters>,
) : GroupParametersReaderService {

    @Activate constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = GroupParametersFactory::class)
        groupParametersFactory: GroupParametersFactory,
    ) : this(
        coordinatorFactory,
        configurationReadService,
        subscriptionFactory,
        groupParametersFactory,
        MemberDataCache.Impl()
    )

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val serviceName = GroupParametersReaderService::class.java.simpleName
        const val CONSUMER_GROUP = "GROUP_PARAMETERS_READER"
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<GroupParametersReaderService>()
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private var impl: InnerGroupParametersReaderService = InactiveImpl

    // for watching the dependencies
    private var dependencyHandle: RegistrationHandle? = null
    // for watching the config changes
    private var configHandle: AutoCloseable? = null
    // for watching the state of the subscription
    private var subscriptionHandle: RegistrationHandle? = null

    private var groupParamsSubscription: CompactedSubscription<String, GroupParametersAvro>? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$serviceName started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$serviceName stopped.")
        coordinator.stop()
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, GroupParameters>>? =
        impl.getAllVersionedRecords()

    override fun get(identity: HoldingIdentity): GroupParameters? = impl.get(identity)

    private interface InnerGroupParametersReaderService : AutoCloseable {
        fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, GroupParameters>>

        fun get(identity: HoldingIdentity): GroupParameters?
    }

    private object InactiveImpl : InnerGroupParametersReaderService {
        override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, GroupParameters>> =
            throw IllegalStateException("$serviceName is currently inactive.")

        override fun get(identity: HoldingIdentity): GroupParameters =
            throw IllegalStateException("$serviceName is currently inactive.")

        override fun close() = Unit
    }

    private inner class ActiveImpl : InnerGroupParametersReaderService {
        override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, GroupParameters>> {
            val recordList: List<VersionedRecord<HoldingIdentity, GroupParameters>> = groupParametersCache.getAll().map {
                object : VersionedRecord<HoldingIdentity, GroupParameters> {
                    override val version = it.value.epoch
                    override val isDeleted = false
                    override val key = it.key
                    override val value = it.value
                }
            }
            return recordList.stream()
        }

        override fun get(identity: HoldingIdentity): GroupParameters? = groupParametersCache.get(identity)

        override fun close() {
            groupParametersCache.clear()
        }
    }

    private fun activate(coordinator: LifecycleCoordinator) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, "Received config and started group parameters topic subscription.")
    }

    private fun deactivate(coordinator: LifecycleCoordinator, msg: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, msg)
        impl.close()
        impl = InactiveImpl
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
        deactivate(coordinator, "Component received stop event.")
        dependencyHandle?.close()
        dependencyHandle = null
        subscriptionHandle?.close()
        subscriptionHandle = null
        configHandle?.close()
        configHandle = null
        groupParamsSubscription?.close()
        groupParamsSubscription = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        logger.info("Handling registration changed event.")
        when (event.status) {
            LifecycleStatus.UP -> {
                if (event.registration == dependencyHandle) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else if (event.registration == subscriptionHandle) {
                    activate(coordinator)
                }
            }
            else -> {
                deactivate(coordinator, "Dependencies are down.")
            }
        }
    }

    private fun handleConfigChange(event: ConfigChangedEvent) {
        logger.info("Handling config changed event.")
        subscriptionHandle?.close()
        subscriptionHandle = null
        groupParamsSubscription?.close()
        groupParamsSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                CONSUMER_GROUP,
                GROUP_PARAMETERS_TOPIC
            ),
            GroupParametersProcessor(groupParametersCache, groupParametersFactory),
            event.config.getConfig(MESSAGING_CONFIG)
        ).also {
            it.start()
            subscriptionHandle = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
        }
    }
}