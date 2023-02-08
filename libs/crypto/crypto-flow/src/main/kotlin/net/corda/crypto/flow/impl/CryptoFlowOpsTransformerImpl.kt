package net.corda.crypto.flow.impl

import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_OP_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_TTL_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_ERROR_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_TOPIC
import net.corda.crypto.impl.createWireRequestContext
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toWire
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.ByIdsFlowQuery
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

/**
 * The crypto operations client to generate messages for flows.
 *
 * @property requestingComponent Name of the component which request the operation,
 * mostly used for logging and monitoring.
 *
 * @property responseTopic - name of the topic where to send the replies to.
 *
 * @property keyEncodingService - service which is used to encode/decode public keys.
 *
 * @property requestValidityWindowSeconds - TTL for the message processing in seconds,
 * the default value is equal to 5 minutes.
 */
@Suppress("TooManyFunctions")
class CryptoFlowOpsTransformerImpl(
    private val serializer: AlgorithmParameterSpecEncodingService,
    private val requestingComponent: String,
    private val responseTopic: String,
    private val keyEncodingService: KeyEncodingService,
    private val requestValidityWindowSeconds: Long = 300
) : CryptoFlowOpsTransformer {

    // TODO This is currently only being used by `SigningServiceImpl.findMySigningKeys` so make it use only full key Ids.
    override fun createFilterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        flowExternalEventContext: ExternalEventContext
    ): FlowOpsRequest {
        return createRequest(
            requestId = UUID.randomUUID().toString(),
            tenantId = tenantId,
            request = ByIdsFlowQuery(
                candidateKeys.map {
                    val keyBytes = keyEncodingService.encodeAsByteArray(it)
                    publicKeyIdFromBytes(keyBytes)
                }
            ),
            flowExternalEventContext = flowExternalEventContext
        )
    }

    override fun createSign(
        requestId: String,
        tenantId: String,
        encodedPublicKeyBytes: ByteArray,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>,
        flowExternalEventContext: ExternalEventContext
    ): FlowOpsRequest {
        return createRequest(
            requestId = requestId,
            tenantId = tenantId,
            request = SignFlowCommand(
                ByteBuffer.wrap(encodedPublicKeyBytes),
                signatureSpec.toWire(serializer),
                ByteBuffer.wrap(data),
                context.toWire()
            ),
            flowExternalEventContext = flowExternalEventContext
        )
    }

    override fun inferRequestType(response: FlowOpsResponse): Class<*>? {
        return when (response.getContextValue(REQUEST_OP_KEY)) {
            SignFlowCommand::class.java.simpleName -> SignFlowCommand::class.java
            ByIdsFlowQuery::class.java.simpleName -> ByIdsFlowQuery::class.java
            else -> null
        }
    }

    override fun transform(response: FlowOpsResponse): Any {
        val ttl = response.getContextValue(REQUEST_TTL_KEY)?.toLong() ?: 300
        val expireAt = response.context.requestTimestamp.plusSeconds(ttl)
        val now = Instant.now()
        if (now >= expireAt) {
            throw IllegalStateException("Response is no longer valid, expired at $expireAt")
        }
        return when (inferRequestType(response)) {
            SignFlowCommand::class.java -> transformCryptoSignatureWithKey(response)
            ByIdsFlowQuery::class.java -> transformCryptoSigningKeys(response)
            else -> throw IllegalArgumentException(
                "Unknown request type: $REQUEST_OP_KEY=${response.getContextValue(REQUEST_OP_KEY)}"
            )
        }
    }

    /**
     * Transforms [CryptoSigningKeys]
     */
    private fun transformCryptoSigningKeys(response: FlowOpsResponse): List<PublicKey> {
        val resp = response.validateAndGet<CryptoSigningKeys>()
        return resp.keys.map {
            keyEncodingService.decodePublicKey(it.publicKey.array())
        }
    }

    /**
     * Transforms [CryptoSignatureWithKey]
     */
    private fun transformCryptoSignatureWithKey(response: FlowOpsResponse): DigitalSignature.WithKey {
        val resp = response.validateAndGet<CryptoSignatureWithKey>()
        return DigitalSignature.WithKey(
            by = keyEncodingService.decodePublicKey(resp.publicKey.array()),
            bytes = resp.bytes.array(),
            context = resp.context.toMap()
        )
    }

    /**
     * Creates [FlowOpsRequest] for specified tenant and operation
     */
    private fun createRequest(
        requestId: String,
        tenantId: String,
        request: Any,
        flowExternalEventContext: ExternalEventContext
    ): FlowOpsRequest =
        FlowOpsRequest(
            createWireRequestContext(
                requestingComponent, requestId, tenantId, KeyValuePairList(
                    listOf(
                        KeyValuePair(REQUEST_OP_KEY, request::class.java.simpleName),
                        KeyValuePair(RESPONSE_TOPIC, responseTopic),
                        KeyValuePair(REQUEST_TTL_KEY, requestValidityWindowSeconds.toString())
                    )
                )
            ),
            request,
            flowExternalEventContext
        )

    /**
     * Transforms map to [KeyValuePairList]
     */
    private fun Map<String, String>.toWire(): KeyValuePairList {
        return KeyValuePairList(
            map {
                KeyValuePair(it.key, it.value)
            }
        )
    }

    /**
     * Returns the value of the context key or null if it's not found.
     */
    private fun FlowOpsResponse.getContextValue(key: String): String? =
        context.other.items.firstOrNull { it.key == key }?.value

    /**
     * Validates that the response doesn't contain error and that it's of expected type.
     *
     * @return The enclosed response object.
     *
     * @throws [IllegalStateException] if the response contains error.
     */
    @Suppress("ThrowsCount")
    private inline fun <reified EXPECTED> FlowOpsResponse.validateAndGet(): EXPECTED {
        if (response is CryptoNoContentValue) {
            val error = getContextValue(RESPONSE_ERROR_KEY)
            if (error.isNullOrBlank()) {
                throw IllegalStateException("Unexpected response value")
            } else {
                throw IllegalStateException("Request failed: $error")
            }
        }
        if (response !is EXPECTED) {
            throw IllegalStateException(
                "Unexpected response type, expected '${EXPECTED::class.java.name}'" +
                        "but received '${response::class.java.name}'"
            )
        }
        return response as EXPECTED
    }
}
