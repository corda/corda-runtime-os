package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistFilteredTransactionsAndSignatures
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService

class UtxoPersistFilteredTransactionsAndSignaturesRequestHandler(
    private val request: PersistFilteredTransactionsAndSignatures,
    private val externalEventContext: ExternalEventContext,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory,
    private val serializationService: SerializationService,
    private val persistenceService: UtxoPersistenceService
) : RequestHandler {

    private companion object {
        const val CORDA_ACCOUNT = "corda.account"
    }

    override fun execute(): List<Record<*, *>> {
        val accountString = externalEventContext.contextProperties.items.find {
            it.key == CORDA_ACCOUNT
        }?.value

        requireNotNull(accountString) { "Account cannot be null" }

        @Suppress("MaxLineLength")
        val filteredTransactionsAndSignatures = serializationService.deserialize<Map<FilteredTransaction, List<DigitalSignatureAndMetadata>>>(
            request.filteredTransactionsAndSignatures.array()
        )
        persistenceService.persistFilteredTransactionsAndSignatures(filteredTransactionsAndSignatures, accountString)

        return listOf(utxoOutputRecordFactory.getPersistTransactionSuccessRecord(externalEventContext))
    }
}
