package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindSignedGroupParameters
import net.corda.data.ledger.persistence.FindSignedGroupParametersResponse
import net.corda.ledger.libs.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import java.nio.ByteBuffer

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
                    listOfNotNull(net.corda.data.membership.SignedGroupParameters(
                        ByteBuffer.wrap(signedGroupParameters?.groupParameters),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap(signedGroupParameters?.mgmSignature?.publicKey),
                            ByteBuffer.wrap(signedGroupParameters?.mgmSignature?.bytes)
                        ),
                        CryptoSignatureSpec(
                            signedGroupParameters?.mgmSignatureSpec?.signatureName,
                            signedGroupParameters?.mgmSignatureSpec?.customDigestName,
                            CryptoSignatureParameterSpec(
                                signedGroupParameters?.mgmSignatureSpec?.params?.javaClass?.name,
                                ByteBuffer.wrap(signedGroupParameters?.mgmSignatureSpec?.params)
                            )
                        )
                    ))
                )
            )
        )
    }
}
