package net.corda.membership.impl.read.reader

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
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
import net.corda.membership.read.GroupParametersReaderService
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.Schemas.Membership.Companion.GROUP_PARAMETERS_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Reference
import java.util.stream.Stream

class GroupParametersReaderServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    /*@Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LayeredPropertyMapFactory::class)
    layeredPropertyMapFactory: LayeredPropertyMapFactory,*/
) : GroupParametersReaderService {
    private companion object {
        val logger = contextLogger()
        const val SERVICE = "GroupParametersReaderService"
        const val CONSUMER_GROUP = "GROUP_PARAMETERS_READER"
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<GroupParametersReaderService>()
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private var impl: InnerGroupParametersReaderService = InactiveImpl

    // for watching the dependencies
    private var dependencyHandle: RegistrationHandle? = null
    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    private var groupParamsSubscription: CompactedSubscription<String, GroupParameters>? = null

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

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, GroupParameters>>? {
        TODO("Not yet implemented")
    }

    override fun get(identity: HoldingIdentity): GroupParameters? {
        TODO("Not yet implemented")
    }

    private interface InnerGroupParametersReaderService : AutoCloseable {
        fun getAllVersionedRecords(): Stream<VersionedRecord<String, GroupParameters>>?

        fun get(identity: HoldingIdentity): GroupParameters?
    }

    private object InactiveImpl : InnerGroupParametersReaderService {
        override fun getAllVersionedRecords(): Stream<VersionedRecord<String, GroupParameters>>? {
            TODO("Not yet implemented")
        }

        override fun get(identity: HoldingIdentity): GroupParameters? {
            TODO("Not yet implemented")
        }

        override fun close() {
            TODO("Not yet implemented")
        }
    }

    private inner class ActiveImpl : InnerGroupParametersReaderService {
        override fun getAllVersionedRecords(): Stream<VersionedRecord<String, GroupParameters>> {
            /*val recordList: List<VersionedRecord<String, GroupParameters>> = groupParametersCache.getAll().map {
                object : VersionedRecord<String, GroupParameters> {
                    override val version = it.value.epoch
                    override val isDeleted = false
                    override val key = it.key.toString()
                    override val value = it.value
                }
            }
            return recordList.stream()*/
            TODO("Not yet implemented")
        }

        override fun get(identity: HoldingIdentity): GroupParameters? = TODO("Not yet implemented")//groupParametersCache.get(identity)

        override fun close() {
        }
    }

    private fun activate(coordinator: LifecycleCoordinator) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl.close()
        impl = InactiveImpl
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange()
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

    private fun handleConfigChange() {
        logger.info("Handling config changed event.")
        /*val subscriptionConfig = SubscriptionConfig(
            CONSUMER_GROUP,
            GROUP_PARAMETERS_TOPIC
        )*/
        groupParamsSubscription?.close()
        /*groupParamsSubscription = subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            GroupParametersProcessor(),
            event.config.getConfig(MESSAGING_CONFIG)
        ).also { it.start() }*/
        activate(coordinator)
    }
}