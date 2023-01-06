package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

// TODO to avoid circular dependency between persistenceservice-signedTransactionFactory, we pass the persistence
// service directly to the create()s instead of proper OSGi injection.

interface UtxoSignedTransactionFactory {
    @Suspendable
    fun create(
        utxoTransactionBuilder: UtxoTransactionBuilderInternal,
        signatories: Iterable<PublicKey>,
        utxoLedgerPersistenceService: UtxoLedgerPersistenceService
    ): UtxoSignedTransaction

    fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>,
        utxoLedgerPersistenceService: UtxoLedgerPersistenceService
    ): UtxoSignedTransaction
}