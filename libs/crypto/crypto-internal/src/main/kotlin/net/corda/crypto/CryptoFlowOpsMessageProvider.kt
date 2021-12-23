package net.corda.crypto

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.flow.FilterMyKeysFlowQuery
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
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
class CryptoFlowOpsMessageProvider(
    private val requestingComponent: String,
    private val schemeMetadata: CipherSchemeMetadata
) {
    private val emptyKeyValuePairList = KeyValuePairList(emptyList())

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
            tenantId = tenantId,
            request = GenerateFreshKeyFlowCommand(null, context.toWire())
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
            tenantId = tenantId,
            request = GenerateFreshKeyFlowCommand(externalId.toString(), context.toWire())
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
     * Processes [CryptoPublicKey]
     */
    fun handleCryptoPublicKey(response: CryptoPublicKey): PublicKey {
        return schemeMetadata.decodePublicKey(response.key.array())
    }

    /**
     * Processes [CryptoPublicKeys]
     */
    fun handleCryptoPublicKeys(response: CryptoPublicKeys): Iterable<PublicKey> {
        return response.keys.map {
            schemeMetadata.decodePublicKey(it.array())
        }
    }

    /**
     * CryptoSignatureWithKey
     */
    fun handleCryptoSignatureWithKey(response: CryptoSignatureWithKey): DigitalSignature.WithKey {
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(response.publicKey.array()),
            bytes = response.bytes.array()
        )
    }

    private fun createRequest(tenantId: String, request: Any): FlowOpsRequest =
        FlowOpsRequest(
            createWireRequestContext(tenantId),
            request
        )

    private fun createWireRequestContext(
        tenantId: String
    ): CryptoRequestContext {
        return CryptoRequestContext(
            requestingComponent,
            Instant.now(),
            UUID.randomUUID().toString(),
            tenantId,
            emptyKeyValuePairList
        )
    }

    private fun Map<String, String>.toWire(): KeyValuePairList {
        return KeyValuePairList(
            map {
                KeyValuePair(it.key, it.value)
            }
        )
    }
}