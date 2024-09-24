package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistSignedGroupParametersIfDoNotExist
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.libs.persistence.utxo.SignatureWithKey
import net.corda.ledger.libs.persistence.utxo.SignedGroupParameters
import net.corda.ledger.libs.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.messaging.api.records.Record

class UtxoPersistSignedGroupParametersIfDoNotExistRequestHandler(
    private val persistSignedGroupParametersIfDoNotExist: PersistSignedGroupParametersIfDoNotExist,
    private val externalEventContext: ExternalEventContext,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val persistenceService: UtxoPersistenceService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        requireNotNull(persistSignedGroupParametersIfDoNotExist.signedGroupParameters.mgmSignature) {
            "SignedGroupParameters needs to be signed."
        }
        requireNotNull(persistSignedGroupParametersIfDoNotExist.signedGroupParameters.mgmSignatureSpec) {
            "SignedGroupParameters needs a signature specification."
        }
        val signedGroupParameters = persistSignedGroupParametersIfDoNotExist.signedGroupParameters
        persistenceService.persistSignedGroupParametersIfDoNotExist(
            SignedGroupParameters(
                signedGroupParameters.groupParameters.array(),
                SignatureWithKey(
                    signedGroupParameters.mgmSignature.publicKey.array(),
                    signedGroupParameters.mgmSignature.bytes.array()
                ),
                net.corda.ledger.libs.persistence.utxo.SignatureSpec(
                    signedGroupParameters.mgmSignatureSpec.signatureName,
                    signedGroupParameters.mgmSignatureSpec.customDigestName,
                    signedGroupParameters.mgmSignatureSpec.params.bytes.array()
                )
            )
        )



        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
            )
        )
    }
}
