package net.corda.crypto

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.flow.FilterMyKeysFlowQuery
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.SignWithSpecFlowCommand
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

/**
 * The crypto operations client to generate messages for flows.
 */
class CryptoFlowOpsTransformer(
    private val requestingComponent: String,
    private val schemeMetadata: CipherSchemeMetadata
) {
    companion object {
        const val REQUEST_OP_KEY = "req.op"
        const val RESPONSE_ERROR_KEY = "res.err"
    }

    /**
     * Creates [FilterMyKeysFlowQuery].
     */
    fun createFilterMyKeys(tenantId: String, candidateKeys: Iterable<PublicKey>): FlowOpsRequest {
        return createRequest(
            tenantId = tenantId,
            request = FilterMyKeysFlowQuery(
                candidateKeys.map {
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it))
                }
            )
        )
    }

    /**
     * Generates [GenerateFreshKeyFlowCommand].
     */
    fun createFreshKey(
        tenantId: String,
        context: Map<String, String> = SigningService.EMPTY_CONTEXT
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
        context: Map<String, String> = SigningService.EMPTY_CONTEXT
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
        context: Map<String, String> = SigningService.EMPTY_CONTEXT
    ): FlowOpsRequest {
        return createRequest(
            tenantId,
            SignFlowCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
    }

    /**
     * Generates [SignWithSpecFlowCommand]
     */
    fun createSign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = SigningService.EMPTY_CONTEXT
    ): FlowOpsRequest {
        return createRequest(
            tenantId,
            SignWithSpecFlowCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                CryptoSignatureSpec(signatureSpec.signatureName, signatureSpec.customDigestName?.name),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
    }

    /**
     * Checks what was the request and transforms using appropriate transformer [CryptoPublicKey]
     */
    fun transform(response: FlowOpsResponse): Any {
        return when (val requestOp = response.getContextValue(REQUEST_OP_KEY)) {
            SignWithSpecFlowCommand::class.java.simpleName -> transformCryptoSignatureWithKey(response)
            SignFlowCommand::class.java.simpleName -> transformCryptoSignatureWithKey(response)
            GenerateFreshKeyFlowCommand::class.java.simpleName -> transformCryptoPublicKey(response)
            FilterMyKeysFlowQuery::class.java.simpleName -> transformCryptoPublicKeys(response)
            else -> IllegalArgumentException("Unknown request type: $REQUEST_OP_KEY=$requestOp")
        }
    }

    /**
     * Transforms [CryptoPublicKey]
     */
    fun transformCryptoPublicKey(response: FlowOpsResponse): PublicKey {
        val resp = response.validateAndGet<CryptoPublicKey>()
        return schemeMetadata.decodePublicKey(resp.key.array())
    }

    /**
     * Transforms [CryptoPublicKeys]
     */
    fun transformCryptoPublicKeys(response: FlowOpsResponse): Iterable<PublicKey> {
        val resp = response.validateAndGet<CryptoPublicKeys>()
        return resp.keys.map {
            schemeMetadata.decodePublicKey(it.array())
        }
    }

    /**
     * Transforms [CryptoSignatureWithKey]
     */
    fun transformCryptoSignatureWithKey(response: FlowOpsResponse): DigitalSignature.WithKey {
        val resp = response.validateAndGet<CryptoSignatureWithKey>()
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(resp.publicKey.array()),
            bytes = resp.bytes.array()
        )
    }

    /**
     * Creates [FlowOpsRequest] for specified tenant and operation
     */
    fun createRequest(tenantId: String, request: Any): FlowOpsRequest =
        FlowOpsRequest(
            createWireRequestContext(tenantId, request),
            request
        )

    /**
     * Creates [CryptoRequestContext] for specified tenant and operation
     */
    fun createWireRequestContext(
        tenantId: String,
        request: Any
    ): CryptoRequestContext {
        return CryptoRequestContext(
            requestingComponent,
            Instant.now(),
            UUID.randomUUID().toString(),
            tenantId,
            KeyValuePairList(
                listOf(KeyValuePair(REQUEST_OP_KEY, request::class.java.simpleName))
            )
        )
    }

    /**
     * Transforms map to [KeyValuePairList]
     */
    fun Map<String, String>.toWire(): KeyValuePairList {
        return KeyValuePairList(
            map {
                KeyValuePair(it.key, it.value)
            }
        )
    }
}

/**
 * Returns the value of the context key or null if it's not found.
 */
fun FlowOpsResponse.getContextValue(key: String): String? =
    context.other.items.firstOrNull { it.key == key }?.value

/**
 * Validates that the response doesn't contain error and that it's of expected type.
 *
 * @return The enclosed response obeject.
 */
inline fun <reified EXPECTED> FlowOpsResponse.validateAndGet(): EXPECTED {
    if (response is CryptoNoContentValue) {
        val error = getContextValue(CryptoFlowOpsTransformer.RESPONSE_ERROR_KEY)
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