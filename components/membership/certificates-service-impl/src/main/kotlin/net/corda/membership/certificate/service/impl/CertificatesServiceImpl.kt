package net.corda.membership.certificate.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.helper.getConfig
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
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.membership.certificate.service.CertificatesService
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CertificatesService::class])
@Suppress("LongParameterList")
class CertificatesServiceImpl internal constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val configurationReadService: ConfigurationReadService,
    override val client: DbCertificateClient,
) : CertificatesService {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = DbConnectionManager::class)
        dbConnectionManager: DbConnectionManager,
        @Reference(service = JpaEntitiesRegistry::class)
        jpaEntitiesRegistry: JpaEntitiesRegistry,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
    ) : this(
        coordinatorFactory,
        subscriptionFactory,
        configurationReadService,
        DbClientImpl(
            dbConnectionManager,
            jpaEntitiesRegistry,
            virtualNodeInfoReadService,
        )
    )

    private companion object {
        val logger = contextLogger()
        const val GROUP_NAME = "membership.certificates.service"
        const val CLIENT_NAME = "membership.certificates.service"
    }

    private var registrationHandle: AutoCloseable? = null
    private var subscriptionRegistrationHandle: AutoCloseable? = null
    private var configHandle: Resource? = null
    private var rpcSubscription: Resource? = null
    private val coordinator = coordinatorFactory.createCoordinator<CertificatesService>(::handleEvent)

    override fun start() {
        coordinator.start()
    }
    override fun stop() {
        coordinator.stop()
    }

    @Suppress("ComplexMethod")
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<DbConnectionManager>()
                    )
                )
            }
            is StopEvent -> {
                coordinator.updateStatus(
                    LifecycleStatus.DOWN,
                    "Component received stop event."
                )
                subscriptionRegistrationHandle?.close()
                subscriptionRegistrationHandle = null
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                rpcSubscription?.close()
                rpcSubscription = null
            }
            is RegistrationStatusChangeEvent -> {
                if (event.registration == registrationHandle) {
                    configHandle?.close()
                    if (event.status == LifecycleStatus.UP) {
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                        )
                    } else {
                        configHandle = null
                        rpcSubscription?.close()
                        rpcSubscription = null
                    }
                } else if (event.registration == subscriptionRegistrationHandle) {
                    if (event.status == LifecycleStatus.UP) {
                        coordinator.updateStatus(
                            LifecycleStatus.UP,
                            "Ready."
                        )
                    } else {
                        coordinator.updateStatus(
                            event.status,
                            "Subscription went down."
                        )
                    }
                } else {
                    logger.warn("Unexpected event $event.")
                }
            }
            is ConfigChangedEvent -> {
                subscriptionRegistrationHandle?.close()
                rpcSubscription?.close()
                rpcSubscription = subscriptionFactory.createRPCSubscription(
                    rpcConfig = RPCConfig(
                        groupName = GROUP_NAME,
                        clientName = CLIENT_NAME,
                        requestTopic = Schemas.Certificates.CERTIFICATES_RPC_TOPIC,
                        requestType = CertificateRpcRequest::class.java,
                        responseType = CertificateRpcResponse::class.java
                    ),
                    responderProcessor = CertificatesProcessor(client),
                    messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
                ).also {
                    subscriptionRegistrationHandle = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
                    it.start()
                }
            }
            else -> {
                logger.warn("Unexpected event $event.")
            }
        }
    }

    override val isRunning = rpcSubscription != null
}
