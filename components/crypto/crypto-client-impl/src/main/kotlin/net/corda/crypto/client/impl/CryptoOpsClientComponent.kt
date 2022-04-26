package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
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
    configurationReadService: ConfigurationReadService
) : AbstractConfigurableComponent<CryptoOpsClientComponent.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    )
), CryptoOpsClient, CryptoOpsProxyClient {
    companion object {
        const val CLIENT_ID = "crypto.ops.rpc"
        const val GROUP_NAME = "crypto.ops.rpc"
    }

    interface Impl : AutoCloseable {
        val ops: CryptoOpsClientImpl
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        ActiveImpl(publisherFactory, schemeMetadata, event)

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> =
        impl.ops.getSupportedSchemes(tenantId, category)

    override fun filterMyKeys(tenantId: String, candidateKeys: Collection<PublicKey>): Collection<PublicKey> =
        impl.ops.filterMyKeys(tenantId, candidateKeys)

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): PublicKey =
        impl.ops.generateKeyPair(tenantId, category, alias, context)

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        context: Map<String, String>
    ): PublicKey =
        impl.ops.generateKeyPair(tenantId, category, alias, externalId, context)

    override fun freshKey(tenantId: String, context: Map<String, String>): PublicKey =
        impl.ops.freshKey(tenantId, context)

    override fun freshKey(tenantId: String, externalId: String, context: Map<String, String>): PublicKey =
        impl.ops.freshKey(tenantId, externalId, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        impl.ops.sign(tenantId, publicKey, data, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        impl.ops.sign(tenantId, publicKey, signatureSpec, data, context)

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

    override fun lookup(tenantId: String, ids: List<String>): List<CryptoSigningKey> =
        impl.ops.lookup(
            tenantId = tenantId,
            ids = ids
        )

    override fun findHSM(tenantId: String, category: String): HSMInfo? =
        impl.ops.findHSM(tenantId, category)

    override fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys =
        impl.ops.filterMyKeysProxy(tenantId, candidateKeys)

    override fun freshKeyProxy(tenantId: String, context: KeyValuePairList): CryptoPublicKey =
        impl.ops.freshKeyProxy(tenantId, context)

    override fun freshKeyProxy(tenantId: String, externalId: String, context: KeyValuePairList): CryptoPublicKey =
        impl.ops.freshKeyProxy(tenantId, externalId, context)

    override fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey =
        impl.ops.signProxy(tenantId, publicKey, data, context)

    internal class InactiveImpl: Impl {
        override val ops: CryptoOpsClientImpl
            get() = throw IllegalStateException("Component is in illegal state.")

        override fun close() = Unit
    }

    internal class ActiveImpl(
        publisherFactory: PublisherFactory,
        schemeMetadata: CipherSchemeMetadata,
        event: ConfigChangedEvent
    ) : Impl {
        private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse> = publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
            ),
            event.config.toMessagingConfig()
        ).also { it.start() }

        override val ops: CryptoOpsClientImpl = CryptoOpsClientImpl(
            schemeMetadata = schemeMetadata,
            sender = sender
        )

        override fun close() {
            sender.stop()
        }
    }
}