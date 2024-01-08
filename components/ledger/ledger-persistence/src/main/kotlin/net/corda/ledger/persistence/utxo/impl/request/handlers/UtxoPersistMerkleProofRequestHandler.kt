package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistMerkleProofIfDoesNotExist
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record

class UtxoPersistMerkleProofRequestHandler(
    private val persistSignedGroupParametersIfDoNotExist: PersistMerkleProofIfDoesNotExist,
    private val externalEventContext: ExternalEventContext,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val persistenceService: UtxoPersistenceService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // TODO Checks?
        persistenceService.persistMerkleProof(
            persistSignedGroupParametersIfDoNotExist.transactionId,
            persistSignedGroupParametersIfDoNotExist.groupId,
            persistSignedGroupParametersIfDoNotExist.treeSize,
            persistSignedGroupParametersIfDoNotExist.leaves,
            persistSignedGroupParametersIfDoNotExist.hashes
        )

        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(emptyList(), KeyValuePairList(emptyList()), null) // TODO what should we return?
            )
        )
    }
}