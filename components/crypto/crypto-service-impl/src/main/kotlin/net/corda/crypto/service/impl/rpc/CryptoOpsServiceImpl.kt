package net.corda.crypto.service.impl.rpc

import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.CryptoOpsService
import net.corda.crypto.impl.LifecycleDependencies
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoOpsService::class])
class CryptoOpsServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = SigningServiceFactory::class)
    private val signingFactory: SigningServiceFactory
) : CryptoOpsService {
    private companion object {
        private val logger = contextLogger()
        const val GROUP_NAME = "crypto.ops.rpc"
        const val CLIENT_NAME = "crypto.ops.rpc"
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<CryptoOpsService>(::eventHandler)

    private var dependencies: LifecycleDependencies? = null

    private var subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>? = null

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        coordinator.start()
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.info("Stopping...")
        coordinator.postEvent(StopEvent())
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Received start event, waiting for UP event from dependencies.")
                dependencies?.close()
                dependencies = LifecycleDependencies(
                    coordinator,
                    SigningServiceFactory::class.java
                )
            }
            is StopEvent -> {
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Registration status change received from dependencies: ${event.status.name}.")
                if (dependencies?.areUpAfter(event) == true) {
                    createResources()
                    logger.info("Setting status UP.")
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    private fun deleteResources() {
        subscription?.close()
        subscription = null
    }

    private fun createResources() {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC)
        val processor = CryptoOpsRpcProcessor(signingFactory)
        val current = subscription
        subscription = subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
            ),
            responderProcessor = processor
        ).also { start() }
        current?.close()
    }
}