package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.ShortHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
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
    configurationReadService: ConfigurationReadService,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService
) : AbstractConfigurableComponent<CryptoOpsClientComponent.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        )
    ),
    configKeys = setOf(MESSAGING_CONFIG, CRYPTO_CONFIG)
), CryptoOpsClient, CryptoOpsProxyClient {
    companion object {
        const val CLIENT_ID = "crypto.ops.rpc.client"
        const val GROUP_NAME = "crypto.ops.rpc.client"
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(publisherFactory, schemeMetadata, digestService, event)

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> =
        impl.ops.getSupportedSchemes(tenantId, category)

    override fun filterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        usingShortIds: Boolean
    ): Collection<PublicKey> =
        impl.ops.filterMyKeys(tenantId, candidateKeys, usingShortIds)

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
    ): DigitalSignature.WithKey =
        impl.ops.sign(tenantId, publicKey, signatureSpec, data, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        digest: DigestAlgorithmName,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
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

    override fun lookupKeysByShortIds(tenantId: String, shortKeyIds: List<ShortHash>): List<CryptoSigningKey> =
        impl.ops.lookupKeysByShortIds(tenantId, shortKeyIds)

    override fun lookupKeysByIds(tenantId: String, keyIds: List<SecureHash>): List<CryptoSigningKey> =
        impl.ops.lookupKeysByIds(tenantId, keyIds)

    // This path is not being currently used - consider removing it
    override fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys =
        impl.ops.filterMyKeysProxy(tenantId, candidateKeys)

    override fun lookupKeysByShortIdsProxy(tenantId: String, candidateKeys: ShortHashes): CryptoSigningKeys =
        impl.ops.lookupKeysByShortIdsProxy(tenantId, candidateKeys)

    override fun lookupKeysByIdsProxy(tenantId: String, keyIds: SecureHashes): CryptoSigningKeys =
        impl.ops.lookupKeysByIdsProxy(tenantId, keyIds)

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
        publisherFactory: PublisherFactory,
        schemeMetadata: CipherSchemeMetadata,
        digestService: PlatformDigestService,
        event: ConfigChangedEvent
    ) : AbstractImpl {
        private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse> = publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
            ),
            event.config.getConfig(MESSAGING_CONFIG)
        ).also { it.start() }

        val ops: CryptoOpsClientImpl = CryptoOpsClientImpl(
            schemeMetadata,
            sender,
            digestService
        )

        override val downstream: DependenciesTracker = DependenciesTracker.Default(setOf(sender.subscriptionName))

        override fun close() {
            sender.close()
        }
    }
}
