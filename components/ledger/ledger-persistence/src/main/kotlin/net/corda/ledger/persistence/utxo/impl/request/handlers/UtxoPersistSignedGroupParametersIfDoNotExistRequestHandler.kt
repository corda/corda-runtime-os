package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistSignedGroupParametersIfDoNotExist
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.libs.persistence.utxo.SignatureSpec
import net.corda.ledger.libs.persistence.utxo.SignatureWithKey
import net.corda.ledger.libs.persistence.utxo.SignedGroupParameters
import net.corda.ledger.libs.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.messaging.api.records.Record
import net.corda.data.membership.SignedGroupParameters as SignedGroupParametersAvro

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
        persistenceService.persistSignedGroupParametersIfDoNotExist(signedGroupParameters.toCorda())

        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
            )
        )
    }
}

private fun SignedGroupParametersAvro.toCorda(): SignedGroupParameters {
    return SignedGroupParameters(
        groupParameters.array(),
        SignatureWithKey(mgmSignature.publicKey.array(), mgmSignature.bytes.array()),
        SignatureSpec(
            mgmSignatureSpec.signatureName,
            mgmSignatureSpec.customDigestName,
            mgmSignatureSpec.params.bytes.array()
        )
    )
}
