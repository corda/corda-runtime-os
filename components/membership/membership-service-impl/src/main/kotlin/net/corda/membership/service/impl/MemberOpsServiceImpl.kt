package net.corda.membership.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.registration.RegistrationProxy
import net.corda.membership.service.MemberOpsService
import net.corda.libs.configuration.helper.getConfig
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
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<MemberOpsService>(::eventHandler)

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

    @Volatile
    private var subscription: RPCSubscription<MembershipRpcRequest, MembershipRpcResponse>? = null

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
            is StartEvent -> {
                logger.info("Received start event, starting wait for UP event from dependencies.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<RegistrationProxy>(),
                        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    )
                )
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(MESSAGING_CONFIG, BOOT_CONFIG)
                    )
                } else {
                    configHandle?.close()
                    configHandle = null
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                createResources(event)
                logger.info("Setting status UP.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                logger.warn("Unexpected event $event!")
            }
        }
    }

    private fun deleteResources() {
        val current = subscription
        subscription = null
        current?.close()
    }

    private fun createResources(event: ConfigChangedEvent) {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Membership.MEMBERSHIP_RPC_TOPIC)
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val processor = MemberOpsServiceProcessor(
            registrationProxy,
            virtualNodeInfoReadService,
            membershipGroupReaderProvider,
            membershipQueryClient
        )
        subscription?.close()
        subscription = subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = Schemas.Membership.MEMBERSHIP_RPC_TOPIC,
                requestType = MembershipRpcRequest::class.java,
                responseType = MembershipRpcResponse::class.java
            ),
            responderProcessor = processor,
            messagingConfig = messagingConfig
        ).also { it.start() }
    }
}
