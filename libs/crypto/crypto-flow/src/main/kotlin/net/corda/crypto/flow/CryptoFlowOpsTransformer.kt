package net.corda.crypto.flow

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignWithSpecFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
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
class CryptoFlowOpsTransformer(
    private val requestingComponent: String,
    private val responseTopic: String,
    private val keyEncodingService: KeyEncodingService,
    private val requestValidityWindowSeconds: Long = 300
) {
    companion object {
        const val REQUEST_OP_KEY = "req.op"
        const val REQUEST_TTL_KEY = "req.ttl"
        const val RESPONSE_TOPIC = "req.resp.topic"
        const val RESPONSE_ERROR_KEY = "res.err"
        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * Creates [FilterMyKeysFlowQuery].
     */
    fun createFilterMyKeys(tenantId: String, candidateKeys: Collection<PublicKey>): FlowOpsRequest {
        return createRequest(
            tenantId = tenantId,
            request = FilterMyKeysFlowQuery(
                candidateKeys.map {
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(it))
                }
            )
        )
    }

    /**
     * Generates [GenerateFreshKeyFlowCommand].
     */
    fun createFreshKey(
        tenantId: String,
        context: Map<String, String> = EMPTY_CONTEXT
    ): FlowOpsRequest {
        return createRequest(
            tenantId,
            GenerateFreshKeyFlowCommand(null, context.toWire())
        )
    }

    /**
     * Generates [GenerateFreshKeyFlowCommand].
     */
    fun createFreshKey(
        tenantId: String,
        externalId: UUID,
        context: Map<String, String> = EMPTY_CONTEXT
    ): FlowOpsRequest {
        return createRequest(
            tenantId,
            GenerateFreshKeyFlowCommand(externalId.toString(), context.toWire())
        )
    }

    /**
     * Generates [SignFlowCommand]
     */
    fun createSign(
        tenantId: String,
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): FlowOpsRequest {
        return createRequest(
            tenantId,
            SignFlowCommand(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
    }

    /**
     * Returns request's type.
     *
     * @throws [IllegalArgumentException] if the request type is not one of
     * [SignWithSpecFlowCommand], [SignFlowCommand], [GenerateFreshKeyFlowCommand], [FilterMyKeysFlowQuery]
     */
    fun inferRequestType(response: FlowOpsResponse): Class<*>? {
        return when (response.getContextValue(REQUEST_OP_KEY)) {
            SignWithSpecFlowCommand::class.java.simpleName -> SignWithSpecFlowCommand::class.java
            SignFlowCommand::class.java.simpleName -> SignFlowCommand::class.java
            GenerateFreshKeyFlowCommand::class.java.simpleName -> GenerateFreshKeyFlowCommand::class.java
            FilterMyKeysFlowQuery::class.java.simpleName -> FilterMyKeysFlowQuery::class.java
            else -> null
        }
    }

    /**
     * Transforms the response type.
     *
     * @return [PublicKey] for [GenerateFreshKeyFlowCommand] request and [CryptoPublicKey] response type,
     * [List<PublicKey>] for [FilterMyKeysFlowQuery] request and [CryptoPublicKeys] response type
     * [DigitalSignature.WithKey] for [SignFlowCommand] or [SignWithSpecFlowCommand] request
     * with [CryptoSignatureWithKey] response type
     *
     * @throws [IllegalArgumentException] if the request type is not one of
     * [SignWithSpecFlowCommand], [SignFlowCommand], [GenerateFreshKeyFlowCommand], [FilterMyKeysFlowQuery]
     * or the response is not one of
     * [CryptoPublicKey], [CryptoPublicKeys], [CryptoSignatureWithKey]
     *
     * @throws [IllegalStateException]  if the response contains error or its TTL is greater than expected.
     */
    fun transform(response: FlowOpsResponse): Any {
        val ttl = response.getContextValue(REQUEST_TTL_KEY)?.toLong() ?: 300
        val expireAt = response.context.requestTimestamp.plusSeconds(ttl)
        val now = Instant.now()
        if (now >= expireAt) {
            throw IllegalStateException("Response is no longer valid, expired at $expireAt")
        }
        return when (inferRequestType(response)) {
            SignWithSpecFlowCommand::class.java -> transformCryptoSignatureWithKey(response)
            SignFlowCommand::class.java -> transformCryptoSignatureWithKey(response)
            GenerateFreshKeyFlowCommand::class.java -> transformCryptoPublicKey(response)
            FilterMyKeysFlowQuery::class.java -> transformCryptoPublicKeys(response)
            else -> throw IllegalArgumentException(
                "Unknown request type: $REQUEST_OP_KEY=${response.getContextValue(REQUEST_OP_KEY)}")
        }
    }

    /**
     * Transforms [CryptoPublicKey]
     */
    private fun transformCryptoPublicKey(response: FlowOpsResponse): PublicKey {
        val resp = response.validateAndGet<CryptoPublicKey>()
        return keyEncodingService.decodePublicKey(resp.key.array())
    }

    /**
     * Transforms [CryptoPublicKeys]
     */
    private fun transformCryptoPublicKeys(response: FlowOpsResponse): List<PublicKey> {
        val resp = response.validateAndGet<CryptoPublicKeys>()
        return resp.keys.map {
            keyEncodingService.decodePublicKey(it.array())
        }
    }

    /**
     * Transforms [CryptoSignatureWithKey]
     */
    private fun transformCryptoSignatureWithKey(response: FlowOpsResponse): DigitalSignature.WithKey {
        val resp = response.validateAndGet<CryptoSignatureWithKey>()
        return DigitalSignature.WithKey(
            by = keyEncodingService.decodePublicKey(resp.publicKey.array()),
            bytes = resp.bytes.array()
        )
    }

    /**
     * Creates [FlowOpsRequest] for specified tenant and operation
     */
    private fun createRequest(tenantId: String, request: Any): FlowOpsRequest =
        FlowOpsRequest(
            createWireRequestContext(tenantId, request),
            request
        )

    /**
     * Creates [CryptoRequestContext] for specified tenant and operation
     */
    private fun createWireRequestContext(
        tenantId: String,
        request: Any
    ): CryptoRequestContext {
        return CryptoRequestContext(
            requestingComponent,
            Instant.now(),
            UUID.randomUUID().toString(),
            tenantId,
            KeyValuePairList(
                listOf(
                    KeyValuePair(REQUEST_OP_KEY, request::class.java.simpleName),
                    KeyValuePair(RESPONSE_TOPIC, responseTopic),
                    KeyValuePair(REQUEST_TTL_KEY, requestValidityWindowSeconds.toString())
                )
            )
        )
    }

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
