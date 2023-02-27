package net.corda.membership.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.RegistrationProxy
import net.corda.membership.service.MemberOpsService
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [MemberOpsService::class])
@Suppress("LongParameterList")
class MemberOpsServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
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
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
) : MemberOpsService {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val RPC_GROUP_NAME = "membership.ops.rpc"
        const val RPC_CLIENT_NAME = "membership.ops.rpc"
        const val ASYNC_GROUP_NAME = "membership.ops.async"

        const val SUBSCRIPTION_RESOURCE = "MemberOpsService.SUBSCRIPTION_RESOURCE"
        const val CONFIG_HANDLE = "MemberOpsService.CONFIG_HANDLE"
        const val COMPONENT_HANDLE = "MemberOpsService.COMPONENT_HANDLE"
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<MemberOpsService>(::eventHandler)

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    private val clock = UTCClock()

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
                    groupName = RPC_GROUP_NAME,
                    clientName = RPC_CLIENT_NAME,
                    requestTopic = Schemas.Membership.MEMBERSHIP_RPC_TOPIC,
                    requestType = MembershipRpcRequest::class.java,
                    responseType = MembershipRpcResponse::class.java
                ),
                responderProcessor = MemberOpsServiceProcessor(
                    virtualNodeInfoReadService,
                    membershipGroupReaderProvider,
                    membershipQueryClient,
                ),
                messagingConfig = messagingConfig
            ).also { it.start() }
            val processor = MemberOpsAsyncProcessor(
                registrationProxy,
                virtualNodeInfoReadService,
                membershipPersistenceClient,
                membershipQueryClient,
                clock,
            )
            val retryManager = CommandsRetryManager(
                messagingConfig = messagingConfig,
                clock = clock,
                publisherFactory = publisherFactory,
                coordinatorFactory = coordinatorFactory,
            )
            val asyncSubscription = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(
                    ASYNC_GROUP_NAME,
                    MEMBERSHIP_ASYNC_REQUEST_TOPIC,
                ),
                processor,
                messagingConfig,
                retryManager,
            ).also {
                it.start()
            }
            val handle = coordinator.followStatusChangesByName(
                setOf(
                    subscription.subscriptionName,
                    asyncSubscription.subscriptionName,
                )
            )
            MembershipSubscriptionAndRegistration(subscription, asyncSubscription, handle, retryManager)
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
        val asyncSubscription: Resource,
        val registrationHandle: RegistrationHandle,
        val retryManager: Resource,
    ) : Resource {
        override fun close() {
            // The close order here is important - closing the subscription first can result in spurious lifecycle
            // events being posted.
            registrationHandle.close()
            subscription.close()
            asyncSubscription.close()
            retryManager.close()
        }
    }
}
