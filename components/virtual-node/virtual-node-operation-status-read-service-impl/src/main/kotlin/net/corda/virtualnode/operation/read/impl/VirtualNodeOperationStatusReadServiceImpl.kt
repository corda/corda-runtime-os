package net.corda.virtualnode.operation.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_OPERATION_STATUS_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusMap
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusFactory
import net.corda.virtualnode.async.operation.events.ErrorReceived
import net.corda.virtualnode.async.operation.events.StatusReceived
import net.corda.virtualnode.async.operation.events.SnapshotReceived
import net.corda.virtualnode.operation.read.VirtualNodeOperationStatusReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [VirtualNodeOperationStatusReadService::class])
class VirtualNodeOperationStatusReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = VirtualNodeOperationStatusFactory::class)
    private val virtualNodeOperationStatusFactory: VirtualNodeOperationStatusFactory,
) : VirtualNodeOperationStatusReadService {

    private var configHandle: Resource? = null

    private companion object {
        val logger = contextLogger()
        const val CONSUMER_GROUP = "VIRTUAL_NODE_OPERATION_STATUS_SERVICE"
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
    )
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<VirtualNodeOperationStatusReadService>(
        dependentComponents,
        ::eventHandler
    )
    private var statusSubscription: CompactedSubscription<String, VirtualNodeOperationStatus>? = null
    private var cache: VirtualNodeOperationStatusMap? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info("Starting Virtual Node Operation Status Read Service.")
                configHandle?.close()
                configHandle =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else {
                    configHandle?.close()
                }
            }
            is ConfigChangedEvent -> {
                if (cache == null) cache = virtualNodeOperationStatusFactory.createStatusMap()
                statusSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, VIRTUAL_NODE_OPERATION_STATUS_TOPIC),
                    virtualNodeOperationStatusFactory.createStatusProcessor(
                        cache!!,
                        ::onSnapshotReceived,
                        ::onNextReceived,
                        ::onErrorReceived,
                    ),
                    event.config.getConfig(MESSAGING_CONFIG)
                )
            }
            is SnapshotReceived -> {
                logger.info("Virtual node operation status topic snapshot received.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StatusReceived -> {
                logger.info("Virtual node operation status received")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is ErrorReceived -> {
                logger.info("Virtual node operation error received")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
//                entityProcessor?.stop()
                statusSubscription?.close()
                logger.debug { "Stopping VirtualNode Operation Status Service." }
            }
        }
    }

    private fun onSnapshotReceived() {
        lifecycleCoordinator.postEvent(SnapshotReceived())
    }

    private fun onNextReceived() {
        lifecycleCoordinator.postEvent(StatusReceived())
    }

    private fun onErrorReceived() {
        lifecycleCoordinator.postEvent(ErrorReceived())
    }

    override fun getByRequestId(requestId: String): VirtualNodeOperationStatus? {
        validateCacheReady(cache)
        return cache!!.get(requestId)
    }

    override fun getByVirtualNodeShortHash(virtualNodeShortHash: String): List<VirtualNodeOperationStatus> {
        validateCacheReady(cache)
        return cache!!.getByVirtualNodeShortHash(virtualNodeShortHash)
    }

    private fun validateCacheReady(cache: VirtualNodeOperationStatusMap?) {
        if (cache == null) {
            throw CordaRuntimeException("VirtualNodeOperationStatus cache not ready")
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}