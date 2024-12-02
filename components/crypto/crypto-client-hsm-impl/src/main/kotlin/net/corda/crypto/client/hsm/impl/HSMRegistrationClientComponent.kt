package net.corda.crypto.client.hsm.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
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
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [HSMRegistrationClient::class])
class HSMRegistrationClientComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : HSMRegistrationClient {
    companion object {
        const val GROUP_NAME = "crypto.hsm.registration.client"
        const val CLIENT_ID = "crypto.hsm.registration.client"

        private val logger = LoggerFactory.getLogger(HSMRegistrationClientComponent::class.java)
    }

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo =
        impl.registrar.assignHSM(tenantId, category, context)

    override fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo =
        impl.registrar.assignSoftHSM(tenantId, category)

    override fun findHSM(tenantId: String, category: String): HSMAssociationInfo? =
        impl.registrar.findHSM(tenantId, category)

    class Impl(sender: RPCSender<HSMRegistrationRequest, HSMRegistrationResponse>) {
        val registrar: HSMRegistrationClientImpl = HSMRegistrationClientImpl(sender)
    }

    private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<HSMRegistrationClient>()
    // VisibleForTesting
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::eventHandler
    )
    private val myName = lifecycleCoordinatorName

    private var _impl: Impl? = null
    val impl: Impl get() {
        val tmp = _impl
        if(tmp == null || lifecycleCoordinator.status != LifecycleStatus.UP) {
            throw IllegalStateException("Component $myName is not ready.")
        }
        return tmp
    }

    private var rpcSender: RPCSender<HSMRegistrationRequest, HSMRegistrationResponse>? = null
    private var rpcSenderRegistrationHandle: RegistrationHandle? = null

    private var configReadServiceRegistrationHandle: RegistrationHandle? = null
    private var configReadServiceIsUp = false
    private var configHandle: AutoCloseable? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.trace { "LifecycleEvent received $myName: $event" }
        when (event) {
            is StartEvent -> {
                configReadServiceRegistrationHandle?.close()
                configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }

            is StopEvent -> {
                onStop()
            }

            is RegistrationStatusChangeEvent -> {
                onRegistrationStatusChangeEvent(coordinator, event)
            }

            is ConfigChangedEvent -> {
                doActivation(event, coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    private fun onRegistrationStatusChangeEvent(
        coordinator: LifecycleCoordinator,
        event: RegistrationStatusChangeEvent
    ) {
        if (event.registration == configReadServiceRegistrationHandle) {
            configHandle?.close()
            if (event.status == LifecycleStatus.UP) {
                logger.trace { "Registering for configuration updates." }
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(MESSAGING_CONFIG, CRYPTO_CONFIG)
                )
                configReadServiceIsUp = true
            } else {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                configReadServiceIsUp = false
            }
        } else {
            if (event.status != LifecycleStatus.UP) {
                coordinator.updateStatus(LifecycleStatus.DOWN)
            } else if (configReadServiceIsUp && _impl != null) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    private fun doActivation(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.trace { "Activating $myName" }
        rpcSenderRegistrationHandle?.close()
        rpcSenderRegistrationHandle = null
        rpcSender?.close()
        rpcSender = createSender(event).also { rpcSender ->
            rpcSender.start()
            rpcSenderRegistrationHandle = coordinator.followStatusChangesByName(setOf(rpcSender.subscriptionName))
            _impl = Impl(rpcSender)
        }
        logger.trace { "Activated $myName" }
    }

    private fun createSender(event: ConfigChangedEvent): RPCSender<HSMRegistrationRequest, HSMRegistrationResponse> =
        publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC,
                requestType = HSMRegistrationRequest::class.java,
                responseType = HSMRegistrationResponse::class.java
            ),
            event.config.getConfig(MESSAGING_CONFIG)
        )

    private fun onStop() {
        rpcSender?.close()
        rpcSender = null
        rpcSenderRegistrationHandle?.close()
        rpcSenderRegistrationHandle = null
        configHandle?.close()
        configHandle = null
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = null
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.trace { "$myName starting..." }
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.trace { "$myName stopping..." }
        lifecycleCoordinator.stop()
    }
}
