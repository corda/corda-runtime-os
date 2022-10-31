package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.TransactionBuilderInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey

interface ConsensualSignedTransactionFactory {
    fun create(
        consensualTransactionBuilder: TransactionBuilderInternal,
        signatories: Iterable<PublicKey>
    ): ConsensualSignedTransaction

    fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): ConsensualSignedTransaction
}