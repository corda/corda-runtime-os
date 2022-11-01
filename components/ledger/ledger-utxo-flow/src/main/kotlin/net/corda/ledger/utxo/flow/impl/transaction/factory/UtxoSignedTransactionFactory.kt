package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.TransactionBuilderInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

interface UtxoSignedTransactionFactory {
    fun create(
        consensualTransactionBuilder: TransactionBuilderInternal,
        signatories: Iterable<PublicKey>
    ): UtxoSignedTransaction

    fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): UtxoSignedTransaction
}