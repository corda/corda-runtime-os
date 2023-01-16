package net.corda.membership.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.registration.RegistrationProxy
import net.corda.membership.service.MemberOpsService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.Resource
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MemberOpsService::class])
@Suppress("LongParameterList")
class MemberOpsServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = RegistrationProxy::class)
    private val registrationProxy: RegistrationProxy,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
): MemberOpsService {
    private companion object {
        private val logger = contextLogger()
        const val GROUP_NAME = "membership.ops.rpc"
        const val CLIENT_NAME = "membership.ops.rpc"

        const val SUBSCRIPTION_RESOURCE = "MemberOpsService.SUBSCRIPTION_RESOURCE"
        const val CONFIG_HANDLE = "MemberOpsService.CONFIG_HANDLE"
        const val COMPONENT_HANDLE = "MemberOpsService.COMPONENT_HANDLE"
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<MemberOpsService>(::eventHandler)

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChangeEvent(event, coordinator)
            else -> { logger.warn("Unexpected event $event!") }
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Received start event, waiting for dependencies.")
        coordinator.createManagedResource(COMPONENT_HANDLE) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<RegistrationProxy>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                    LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                    LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                )
            )
        }
    }

    private fun handleDependencyRegistrationChange(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                coordinator.createManagedResource(CONFIG_HANDLE) {
                    logger.info("Registering for configuration updates.")
                    configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                }
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                coordinator.closeManagedResources(setOf(SUBSCRIPTION_RESOURCE, CONFIG_HANDLE))
            }
        }
    }

    private fun handleSubscriptionRegistrationChange(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        logger.info("Handling subscription registration change.")
        when (event.status) {
            LifecycleStatus.UP -> coordinator.updateStatus(LifecycleStatus.UP)
            else -> coordinator.updateStatus(LifecycleStatus.DOWN, "Subscription is DOWN.")
        }
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        val subHandle = coordinator.getManagedResource<MembershipSubscriptionAndRegistration>(
            SUBSCRIPTION_RESOURCE
        )?.registrationHandle
        if (event.registration == subHandle) {
            handleSubscriptionRegistrationChange(event, coordinator)
        } else {
            handleDependencyRegistrationChange(event, coordinator)
        }
    }

    private fun handleConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        recreateSubscription(coordinator, event.config.getConfig(MESSAGING_CONFIG))
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun recreateSubscription(coordinator: LifecycleCoordinator, messagingConfig: SmartConfig) {
        coordinator.createManagedResource(SUBSCRIPTION_RESOURCE) {
            logger.info("Creating RPC subscription for '{}' topic", Schemas.Membership.MEMBERSHIP_RPC_TOPIC)
            val subscription = subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = GROUP_NAME,
                    clientName = CLIENT_NAME,
                    requestTopic = Schemas.Membership.MEMBERSHIP_RPC_TOPIC,
                    requestType = MembershipRpcRequest::class.java,
                    responseType = MembershipRpcResponse::class.java
                ),
                responderProcessor = MemberOpsServiceProcessor(
                    registrationProxy,
                    virtualNodeInfoReadService,
                    membershipGroupReaderProvider,
                    membershipQueryClient,
                ),
                messagingConfig = messagingConfig
            ).also { it.start() }
            val handle = coordinator.followStatusChangesByName(setOf(subscription.subscriptionName))
            MembershipSubscriptionAndRegistration(subscription, handle)
        }
    }

    /**
     * Pair up the subscription and the registration handle to that subscription.
     *
     * This allows us to enforce the close order on these two resources, which prevents an accidental extra DOWN event
     * from propagating when we're recreating the subscription.
     */
    private class MembershipSubscriptionAndRegistration(
        val subscription: RPCSubscription<MembershipRpcRequest, MembershipRpcResponse>,
        val registrationHandle: RegistrationHandle
    ) : Resource {
        override fun close() {
            // The close order here is important - closing the subscription first can result in spurious lifecycle
            // events being posted.
            registrationHandle.close()
            subscription.close()
        }
    }
}
