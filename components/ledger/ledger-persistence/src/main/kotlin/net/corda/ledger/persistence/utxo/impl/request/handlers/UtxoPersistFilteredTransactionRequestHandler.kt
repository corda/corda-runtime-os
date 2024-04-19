package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.crypto.core.SecureHashImpl
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistFilteredTransactionsAndSignatures
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.persistence.common.LedgerPersistenceUtils.findAccount
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateRef

class UtxoPersistFilteredTransactionRequestHandler(
    private val persistFilteredTransactions: PersistFilteredTransactionsAndSignatures,
    private val externalEventContext: ExternalEventContext,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val persistenceService: UtxoPersistenceService,
    private val serializationService: SerializationService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val filteredTransactionsAndSignatures =
            serializationService.deserialize<Map<FilteredTransaction, List<DigitalSignatureAndMetadata>>>(
                persistFilteredTransactions.filteredTransactionsAndSignatures.array()
            )

        persistenceService.persistFilteredTransactions(
            filteredTransactionsAndSignatures,
            persistFilteredTransactions.inputStateRefs.map { stateRef ->
                StateRef(
                    SecureHashImpl(stateRef.transactionId.algorithm, stateRef.transactionId.bytes.array()),
                    stateRef.index
                )
            },
            persistFilteredTransactions.referenceStateRefs.map { stateRef ->
                StateRef(
                    SecureHashImpl(stateRef.transactionId.algorithm, stateRef.transactionId.bytes.array()),
                    stateRef.index
                )
            },
            externalEventContext.findAccount()
        )

        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
            )
        )
    }
}
