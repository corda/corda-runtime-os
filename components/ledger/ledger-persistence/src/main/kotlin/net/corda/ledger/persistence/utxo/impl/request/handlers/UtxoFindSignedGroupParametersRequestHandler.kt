package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindSignedGroupParameters
import net.corda.data.ledger.persistence.FindSignedGroupParametersResponse
import net.corda.ledger.libs.persistence.utxo.SignedGroupParameters
import net.corda.ledger.libs.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import java.nio.ByteBuffer
import net.corda.data.membership.SignedGroupParameters as SignedGroupParametersAvro

class UtxoFindSignedGroupParametersRequestHandler(
    private val findSignedGroupParameters: FindSignedGroupParameters,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val responseFactory: ResponseFactory
) : RequestHandler {
    override fun execute(): List<Record<String, FlowEvent>> {
        val signedGroupParameters = persistenceService.findSignedGroupParameters(
            findSignedGroupParameters.hash,
        )

        return listOf(
            responseFactory.successResponse(
                externalEventContext,
                FindSignedGroupParametersResponse(
                    listOfNotNull(signedGroupParameters?.toAvro())
                )
            )
        )
    }
}

private fun SignedGroupParameters.toAvro(): SignedGroupParametersAvro {
    return SignedGroupParametersAvro(
        ByteBuffer.wrap(groupParameters),
        CryptoSignatureWithKey(ByteBuffer.wrap(mgmSignature.publicKey), ByteBuffer.wrap(mgmSignature.bytes)),
        CryptoSignatureSpec(
            mgmSignatureSpec.signatureName,
            mgmSignatureSpec.customDigestName,
            CryptoSignatureParameterSpec(
                mgmSignatureSpec.params?.javaClass?.name,
                ByteBuffer.wrap(mgmSignatureSpec.params)
            )
        )
    )
}
