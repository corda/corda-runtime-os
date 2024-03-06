package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
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
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.PublicKey

@Suppress("TooManyFunctions")
@Component(service = [CryptoOpsClient::class, CryptoOpsProxyClient::class])
class CryptoOpsClientComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
) : CryptoOpsClient, CryptoOpsProxyClient {
    @Suppress("LongParameterList")
    constructor(
        coordinatorFactory: LifecycleCoordinatorFactory,
        publisherFactory: PublisherFactory,
        schemeMetadata: CipherSchemeMetadata,
        configurationReadService: ConfigurationReadService,
        digestService: PlatformDigestService,
        retriesLimit: Int = 3,
    ) : this(
        coordinatorFactory,
        publisherFactory,
        schemeMetadata,
        configurationReadService,
        digestService
    ) {
        retries = retriesLimit
    }

    companion object {
        const val CLIENT_ID = "crypto.ops.rpc.client"
        const val GROUP_NAME = "crypto.ops.rpc.client"
        var retries = 3

        private val logger = LoggerFactory.getLogger(CryptoOpsClient::class.java)
    }

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> =
        impl.ops.getSupportedSchemes(tenantId, category)

    override fun filterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        usingFullIds: Boolean
    ): Collection<PublicKey> =
        impl.ops.filterMyKeys(tenantId, candidateKeys, usingFullIds)

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey =
        impl.ops.generateKeyPair(tenantId, category, alias, scheme, context)

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey =
        impl.ops.generateKeyPair(tenantId, category, alias, externalId, scheme, context)

    override fun freshKey(
        tenantId: String,
        category: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey =
        impl.ops.freshKey(tenantId, category, scheme, context)

    override fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey =
        impl.ops.freshKey(tenantId, category, externalId, scheme, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignatureWithKey =
        impl.ops.sign(tenantId, publicKey, signatureSpec, data, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        digest: DigestAlgorithmName,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignatureWithKey {
        val signatureSpec = schemeMetadata.inferSignatureSpec(publicKey, digest)
        require(signatureSpec != null) {
            "Failed to infer the signature spec for key=${publicKey.publicKeyId()} " +
                    " (${schemeMetadata.findKeyScheme(publicKey).codeName}:${digest.name})"
        }
        return impl.ops.sign(tenantId, publicKey, signatureSpec, data, context)
    }

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey> =
        impl.ops.lookup(
            tenantId = tenantId,
            skip = skip,
            take = take,
            orderBy = orderBy,
            filter = filter
        )

    override fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey> =
        impl.ops.lookupKeysByIds(tenantId, keyIds)

    override fun lookupKeysByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): List<CryptoSigningKey> =
        impl.ops.lookupKeysByFullIds(tenantId, fullKeyIds)

    // This path is not being currently used - consider removing it
    override fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys =
        impl.ops.filterMyKeysProxy(tenantId, candidateKeys)

    override fun lookupKeysByFullIdsProxy(tenantId: String, fullKeyIds: SecureHashes): CryptoSigningKeys =
        impl.ops.lookupKeysByFullIdsProxy(tenantId, fullKeyIds)

    override fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        signatureSpec: CryptoSignatureSpec,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey = impl.ops.signProxy(tenantId, publicKey, signatureSpec, data, context)

    override fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) = impl.ops.createWrappingKey(hsmId, failIfExists, masterKeyAlias, context)

    override fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray = impl.ops.deriveSharedSecret(tenantId, publicKey, otherPublicKey, context)

    class Impl(
        rpcSender: RPCSender<RpcOpsRequest, RpcOpsResponse>,
        schemeMetadata: CipherSchemeMetadata,
        digestService: PlatformDigestService,
        retries: Int = 3,
    ) {
        val ops: CryptoOpsClientImpl = CryptoOpsClientImpl(
            schemeMetadata,
            rpcSender,
            digestService,
            rpcRetries = retries
        )
    }

    private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
    // VisibleForTesting
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::handleEvent
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

    private var rpcSender: RPCSender<RpcOpsRequest, RpcOpsResponse>? = null
    private var rpcSenderRegistrationHandle: RegistrationHandle? = null

    private var configReadServiceRegistrationHandle: RegistrationHandle? = null
    private var configReadServiceIsUp = false
    private var configHandle: AutoCloseable? = null

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
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
            _impl = Impl(rpcSender, schemeMetadata, digestService)
        }
        logger.trace { "Activated $myName" }
    }

    private fun createSender(event: ConfigChangedEvent): RPCSender<RpcOpsRequest, RpcOpsResponse> =
        publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
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
