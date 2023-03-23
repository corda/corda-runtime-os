package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.service.CryptoOpsBusService
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.impl.SigningServiceImpl
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.InvalidParameterException

@Suppress("LongParameterList")
@Component(service = [CryptoOpsBusService::class])
class CryptoOpsBusServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : AbstractConfigurableComponent<CryptoOpsBusServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoOpsBusService>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        )
    ),
    configKeys = setOf(
        MESSAGING_CONFIG,
        CRYPTO_CONFIG
    )
), CryptoOpsBusService {
    private companion object {
        const val GROUP_NAME = "crypto.ops.rpc"
        const val CLIENT_NAME = "crypto.ops.rpc"
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC)
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val processor = CryptoOpsBusProcessor(
            SigningServiceImpl(
w                cryptoServiceFactory = cryptoServiceFactory,
                schemeMetadata = schemeMetadata,
                digestService = digestService,
                keyEncodingService = keyEncodingService,
                dbConnectionManager = dbConnectionManager,
                jpaEntitiesRegistry = jpaEntitiesRegistry,
                virtualNodeInfoReadService = virtualNodeInfoReadService,
                layeredPropertyMapFactory = layeredPropertyMapFactory,
                config = event.config.get(CRYPTO_CONFIG)
                    ?: throw InvalidParameterException("$CRYPTO_CONFIG missing from config")
            )
        )
        return Impl(
            subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = GROUP_NAME,
                    clientName = CLIENT_NAME,
                    requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                    requestType = RpcOpsRequest::class.java,
                    responseType = RpcOpsResponse::class.java
                ),
                responderProcessor = processor,
                messagingConfig = messagingConfig
            ).also { it.start() }
        )
    }

    class Impl(
        val subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>
    ) : AbstractImpl {
        override val downstream: DependenciesTracker =
            DependenciesTracker.Default(setOf(subscription.subscriptionName))

        override fun close() {
            subscription.close()
            super.close()
        }
    }
}