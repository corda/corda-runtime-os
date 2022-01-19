package net.corda.crypto.client

import net.corda.crypto.CryptoOpsClientComponent
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.impl.stopGracefully
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.ops.rpc.HSMKeyDetails
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
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
import java.security.PublicKey
import java.util.UUID

@Suppress("TooManyFunctions")
@Component(service = [CryptoOpsClientComponent::class])
class CryptoOpsClientComponentImpl :
    AbstractComponent<CryptoOpsClientComponentImpl.Resources>(),
    CryptoOpsClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.ops.rpc"
        const val GROUP_NAME = "crypto.ops.rpc"

        private inline val Resources?.instance: CryptoOpsClientImpl
            get() = this?.ops ?: throw IllegalStateException("The component haven't been initialised.")
    }

    @Volatile
    @Reference(service = LifecycleCoordinatorFactory::class)
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory

    @Volatile
    @Reference(service = PublisherFactory::class)
    lateinit var publisherFactory: PublisherFactory

    @Volatile
    @Reference(service = CipherSchemeMetadata::class)
    lateinit var schemeMetadata: CipherSchemeMetadata

    @Activate
    fun activate() {
        setup(
            coordinatorFactory,
            LifecycleCoordinatorName.forComponent<CryptoOpsClientComponent>()
        )
        createResources()
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

    override fun allocateResources(): Resources = Resources(publisherFactory, schemeMetadata)

    class Resources(
        publisherFactory: PublisherFactory,
        schemeMetadata: CipherSchemeMetadata
    ) : AutoCloseable {
        private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse> = publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
            )
        ).also { it.start() }
        internal val ops: CryptoOpsClientImpl = CryptoOpsClientImpl(
            schemeMetadata = schemeMetadata,
            sender = sender
        )
        override fun close() {
            sender.stopGracefully()
        }
    }
}