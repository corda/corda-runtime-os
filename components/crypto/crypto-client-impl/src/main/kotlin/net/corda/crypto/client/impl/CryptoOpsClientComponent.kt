package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.HSMKeyDetails
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
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
import java.util.UUID

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
) : AbstractComponent<CryptoOpsClientComponent.Resources>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
    configurationReadService,
), CryptoOpsClient, CryptoOpsProxyClient {
    companion object {
        const val CLIENT_ID = "crypto.ops.rpc"
        const val GROUP_NAME = "crypto.ops.rpc"

        private inline val Resources?.instance: CryptoOpsClientImpl
            get() = this?.ops ?: throw IllegalStateException("The component haven't been initialised.")
    }

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> =
        resources.instance.getSupportedSchemes(tenantId, category)

    override fun findPublicKey(tenantId: String, alias: String): PublicKey? =
        resources.instance.findPublicKey(tenantId, alias)

    override fun filterMyKeys(tenantId: String, candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> =
        resources.instance.filterMyKeys(tenantId, candidateKeys)

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): PublicKey =
        resources.instance.generateKeyPair(tenantId, category, alias, context)

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

    override fun findHSMKey(tenantId: String, alias: String): HSMKeyDetails? =
        resources.instance.findHSMKey(tenantId, alias)

    override fun findHSMKey(tenantId: String, publicKey: PublicKey): HSMKeyDetails? =
        resources.instance.findHSMKey(tenantId, publicKey)

    override fun findHSM(tenantId: String, category: String): HSMInfo? =
        resources.instance.findHSM(tenantId, category)

    override fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoPublicKeys =
        resources.instance.filterMyKeysProxy(tenantId, candidateKeys)

    override fun freshKeyProxy(tenantId: String, context: KeyValuePairList): CryptoPublicKey =
        resources.instance.freshKeyProxy(tenantId, context)

    override fun freshKeyProxy(tenantId: String, externalId: UUID, context: KeyValuePairList): CryptoPublicKey =
        resources.instance.freshKeyProxy(tenantId, externalId, context)

    override fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey =
        resources.instance.signProxy(tenantId, publicKey, data, context)

    override fun allocateResources(event: ConfigChangedEvent): Resources {
        logger.info("Creating ${Resources::class.java.name}")
        return Resources(publisherFactory, schemeMetadata, event)
    }

    class Resources(
        publisherFactory: PublisherFactory,
        schemeMetadata: CipherSchemeMetadata,
        event: ConfigChangedEvent
    ) : AutoCloseable {
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

        internal val ops: CryptoOpsClientImpl = CryptoOpsClientImpl(
            schemeMetadata = schemeMetadata,
            sender = sender
        )

        override fun close() {
            sender.stop()
        }
    }
}