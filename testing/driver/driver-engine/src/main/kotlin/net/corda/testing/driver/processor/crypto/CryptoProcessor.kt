package net.corda.testing.driver.processor.crypto

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toSignatureSpec
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.ByIdsFlowQuery
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.messaging.api.records.Record
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_RANKING
import net.corda.testing.driver.config.SmartConfigProvider
import net.corda.testing.driver.processor.ExternalProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("LongParameterList")
@Component(
    service = [ CryptoProcessor::class, CryptoService::class ],
    property = [ DRIVER_SERVICE ]
)
@ServiceRanking(DRIVER_SERVICE_RANKING)
class CryptoProcessor private constructor(
    private val schemaMetadata: CipherSchemeMetadata,
    private val signingKeyProvider: SigningKeyProvider,
    private val stringDeserializer: CordaAvroDeserializer<String>,
    private val anyDeserializer: CordaAvroDeserializer<Any>,
    private val anySerializer: CordaAvroSerializer<Any>,
    private val cryptoService: CryptoService
) : CryptoService by cryptoService, ExternalProcessor {

    @Suppress("unused")
    @Activate
    constructor(
        @Reference
        schemaMetadata: CipherSchemeMetadata,
        @Reference
        signingKeyProvider: SigningKeyProvider,
        @Reference
        cryptoServiceProvider: CryptoServiceProvider,
        @Reference
        smartConfigProvider: SmartConfigProvider,
        @Reference
        cordaAvroSerializationFactory: CordaAvroSerializationFactory
    ) : this(
        schemaMetadata,
        signingKeyProvider,
        stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java),
        anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java),
        anySerializer = cordaAvroSerializationFactory.createAvroSerializer<Any> {},
        cryptoService = cryptoServiceProvider.getInstance(smartConfigProvider.smartConfig)
    )

    override fun processEvent(record: Record<*, *>): List<Record<String, FlowEvent>> {
        val flowId = requireNotNull((record.key as? ByteArray)?.let(stringDeserializer::deserialize)) {
            "Invalid or missing flow ID"
        }
        val request = requireNotNull((record.value as? ByteArray)?.let(anyDeserializer::deserialize) as? FlowOpsRequest) {
            "Invalid or missing FlowOpsRequest"
        }

        val responseTimestamp = Instant.now()
        val requestContext = request.context
        val responseContext = CryptoResponseContext.newBuilder()
            .setResponseTimestamp(responseTimestamp)
            .setRequestingComponent(requestContext.requestingComponent)
            .setRequestTimestamp(requestContext.requestTimestamp)
            .setRequestId(requestContext.requestId)
            .setTenantId(requestContext.tenantId)
            .setOther(requestContext.other)
            .build()
        val response = FlowOpsResponse.newBuilder()
            .setContext(responseContext)
            .setResponse(processRequest(requestContext.tenantId, request.request))
            .setException(null)
            .build()
        val externalEventResponse = ExternalEventResponse.newBuilder()
            .setRequestId(request.flowExternalEventContext.requestId)
            .setPayload(ByteBuffer.wrap(anySerializer.serialize(response)))
            .setTimestamp(responseTimestamp)
            .setError(null)
            .build()
        return listOf(
            Record(record.topic, flowId, FlowEvent(flowId, externalEventResponse))
        )
    }

    private fun processRequest(tenantId: String, request: Any): Any {
        return when(request) {
            is SignFlowCommand -> {
                val publicKey = schemaMetadata.decodePublicKey(request.publicKey.array())
                val keyScheme = schemaMetadata.findKeyScheme(publicKey)
                val signingKey = requireNotNull(signingKeyProvider.getSigningKey(tenantId, publicKey)) {
                    "Signing key missing for tenantId=$tenantId, publicKey=$publicKey"
                }
                CryptoSignatureWithKey(
                    request.publicKey,
                    ByteBuffer.wrap(cryptoService.sign(
                        spec = SigningWrappedSpec(
                            keyMaterialSpec = KeyMaterialSpec(
                                keyMaterial = requireNotNull(signingKey.keyMaterial) {
                                    "keyMaterial should not be null"
                                },
                                wrappingKeyAlias = signingKey.wrappingKeyAlias,
                                encodingVersion = requireNotNull(signingKey.encodingVersion) {
                                    "encodingVersion should not be null"
                                }
                            ),
                            publicKey = publicKey,
                            keyScheme = keyScheme,
                            signatureSpec = request.signatureSpec.toSignatureSpec(schemaMetadata)
                        ),
                        data = request.bytes.array(),
                        context = request.context.toMap()
                    ))
                )
            }
            is ByIdsFlowQuery -> {
                CryptoSigningKeys(
                    signingKeyProvider.getSigningKeys(tenantId, request.fullKeyIds.hashes.map {
                        SecureHashImpl(it.algorithm, it.bytes.array())
                    })
                )
            }
            else -> throw IllegalStateException("Unknown request ${request::class.java.name}")
        }
    }
}
