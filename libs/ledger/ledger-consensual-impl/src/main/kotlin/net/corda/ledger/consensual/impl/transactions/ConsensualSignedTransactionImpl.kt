package net.corda.ledger.consensual.impl.transactions

import net.corda.ledger.common.impl.transactions.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

class ConsensualSignedTransactionImpl(
    val wireTransaction: WireTransaction,
    override val sigs: List<DigitalSignatureAndMetadata>
    ): ConsensualSignedTransaction
{

    init {
        require(sigs.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
    }

    override val id: SecureHash
        get() = wireTransaction.id

    override fun toLedgerTransaction(serializer: SerializationService): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializer)

    /** Returns the same transaction but with an additional (unchecked) signature. */
    override fun withAdditionalSignature(sig: DigitalSignatureAndMetadata): ConsensualSignedTransaction =
        ConsensualSignedTransactionImpl(wireTransaction, sigs + sig)

    /** Returns the same transaction but with an additional (unchecked) signatures. */
    override fun withAdditionalSignatures(sigList: Iterable<DigitalSignatureAndMetadata>): ConsensualSignedTransaction =
        ConsensualSignedTransactionImpl(wireTransaction, sigs + sigList)
}