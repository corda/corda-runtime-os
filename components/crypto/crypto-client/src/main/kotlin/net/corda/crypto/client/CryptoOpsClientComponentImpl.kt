package net.corda.crypto.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.config.CryptoRpcConfig
import net.corda.crypto.component.config.rpc
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.component.lifecycle.NewCryptoConfigReceived
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey
import java.time.Duration
import java.util.UUID

@Component(service = [HSMRegistrarClientComponent::class])
class CryptoOpsClientComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : AbstractComponent<CryptoOpsClientComponentImpl.Resources>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<CryptoOpsClientComponent>()
), CryptoOpsClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.ops.rpc"
        const val GROUP_NAME = "crypto.ops.rpc"

        inline val Resources?.instance: CryptoOpsPublisher
            get() = this?.ops ?: throw IllegalStateException("The component haven't been initialised.")
    }

    private var configHandle: AutoCloseable? = null
    private var config: CryptoRpcConfig? = null
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var schemeMetadata: CipherSchemeMetadata

    @Reference(service = PublisherFactory::class)
    fun publisherFactoryRef(ref: PublisherFactory) {
        publisherFactory = ref
        createResources()
    }

    @Reference(service = CipherSchemeMetadata::class)
    fun publisherFactoryRef(schemeMetadata: CipherSchemeMetadata) {
        this.schemeMetadata = schemeMetadata
        createResources()
    }

    override fun findPublicKey(tenantId: String, alias: String): PublicKey? =
        resources.instance.findPublicKey(tenantId, alias)

    override fun filterMyKeys(tenantId: String, candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> =
        resources.instance.filterMyKeys(tenantId, candidateKeys)

    override fun freshKey(tenantId: String, context: Map<String, String>): PublicKey =
        resources.instance.freshKey(tenantId, context)

    override fun freshKey(tenantId: String, externalId: UUID, context: Map<String, String>): PublicKey =
        resources.instance.freshKey(tenantId, externalId, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        resources.instance.sign(tenantId, publicKey, data, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        resources.instance.sign(tenantId, publicKey, signatureSpec, data, context)

    override fun sign(tenantId: String, alias: String, data: ByteArray, context: Map<String, String>): ByteArray =
        resources.instance.sign(tenantId, alias, data, context)

    override fun sign(
        tenantId: String,
        alias: String,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray =
        resources.instance.sign(tenantId, alias, signatureSpec, data, context)

    override fun findHSMAlias(tenantId: String, alias: String): String? =
        resources.instance.findHSMAlias(tenantId, alias)

    override fun getHSM(tenantId: String, category: String): HSMInfo? =
        resources.instance.getHSM(tenantId, category)

    override fun start() {
        logger.info("Starting coordinator.")
        coordinator.start()
    }

    override fun stop() {
        super.stop()
        releaseConfig()
    }

    override fun handleCoordinatorEvent(event: LifecycleEvent) {
        when (event) {
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    closeResources()
                    releaseConfig()
                }
            }
            is NewCryptoConfigReceived -> {
                config = event.config.rpc
                createResources()
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    override fun readyCreateResources(): Boolean = configHandle != null

    private fun onConfigChange(keys: Set<String>, config: Map<String, SmartConfig>) {
        if (ConfigKeys.CRYPTO_CONFIG in keys) {
            val newConfig = config[ConfigKeys.CRYPTO_CONFIG]
            val libraryConfig = if(newConfig == null || newConfig.isEmpty) {
                CryptoLibraryConfigImpl(emptyMap())
            } else {
                CryptoLibraryConfigImpl(newConfig.root().unwrapped())
            }
            coordinator.postEvent(NewCryptoConfigReceived(libraryConfig))
        }
    }

    override fun allocateResources(): Resources = Resources(publisherFactory, schemeMetadata, config!!)

    private fun releaseConfig() {
        val tmp = configHandle
        configHandle = null
        tmp?.closeGracefully()
    }

    class Resources(
        publisherFactory: PublisherFactory,
        schemeMetadata: CipherSchemeMetadata,
        config: CryptoRpcConfig
    ) : AutoCloseable {
        private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse> = publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
            )
        )
        val ops: CryptoOpsPublisher = CryptoOpsPublisher(
            schemeMetadata = schemeMetadata,
            sender = sender,
            clientRetries = config.clientRetries,
            clientTimeout = Duration.ofMillis(config.clientTimeoutMillis)
        )
        override fun close() {
            sender.closeGracefully()
        }
    }
}