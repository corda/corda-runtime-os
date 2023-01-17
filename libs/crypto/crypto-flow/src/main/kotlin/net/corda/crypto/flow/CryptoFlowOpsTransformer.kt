package net.corda.crypto.flow

import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysByIdsFlowQuery
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * The crypto operations client to generate messages for flows.
 */
interface CryptoFlowOpsTransformer {

    companion object {
        const val REQUEST_OP_KEY = "req.op"
        const val REQUEST_TTL_KEY = "req.ttl"
        const val RESPONSE_TOPIC = "req.resp.topic"
        const val RESPONSE_ERROR_KEY = "res.err"

        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * Creates a crypto request to filter which of the specified keys are owned by the [tenantId].
     */
    fun createFilterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        flowExternalEventContext: ExternalEventContext
    ): FlowOpsRequest

    /**
     * Generates [SignFlowCommand]
     */
    @Suppress("LongParameterList")
    fun createSign(
        requestId: String,
        tenantId: String,
        encodedPublicKeyBytes: ByteArray,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT,
        flowExternalEventContext: ExternalEventContext
    ): FlowOpsRequest

    /**
     * Returns request's type.
     *
     * @throws [IllegalArgumentException] if the request type is not one of [SignFlowCommand], [GenerateFreshKeyFlowCommand],
     * [FilterMyKeysByIdsFlowQuery]
     */
    fun inferRequestType(response: FlowOpsResponse): Class<*>?

    /**
     * Transforms the response type.
     *
     * @return [PublicKey] for [GenerateFreshKeyFlowCommand] request and [CryptoPublicKey] response type, [List<PublicKey>] for
     * [FilterMyKeysByIdsFlowQuery] request and [CryptoSigningKeys] response type [DigitalSignature.WithKey] for [SignFlowCommand] or
     * [SignWithSpecFlowCommand] request with [CryptoSignatureWithKey] response type
     *
     * @throws [IllegalArgumentException] if the request type is not one of [SignWithSpecFlowCommand], [SignFlowCommand],
     * [GenerateFreshKeyFlowCommand], [FilterMyKeysByIdsFlowQuery] or the response is not one of [CryptoPublicKey], [CryptoSigningKeys],
     * [CryptoSignatureWithKey]
     *
     * @throws [IllegalStateException]  if the response contains error or its TTL is greater than expected.
     */
    fun transform(response: FlowOpsResponse): Any
}