package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

interface ConsensualSignedTransactionFactory {
    @Suspendable
    fun create(
        consensualTransactionBuilder: ConsensualTransactionBuilder,
    ): ConsensualSignedTransaction

    fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): ConsensualSignedTransaction
}