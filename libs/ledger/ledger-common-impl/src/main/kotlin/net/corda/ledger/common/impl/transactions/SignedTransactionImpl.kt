package net.corda.ledger.common.impl.transactions

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.common.transactions.LedgerTransaction
import net.corda.v5.ledger.common.transactions.SignedTransaction
import net.corda.v5.ledger.common.transactions.WireTransaction
import java.security.PublicKey

class SignedTransactionImpl(
    override val wireTransaction: WireTransaction,
    override val sigs: List<DigitalSignatureAndMetadata>
    ) :SignedTransaction
{
    override val ledgerTransaction: LedgerTransaction
        get() = TODO("Not yet implemented")

    init {
        require(sigs.isNotEmpty()) {
            "Tried to instantiate a ${SignedTransactionImpl::class.java.simpleName} without any signatures "
        }
    }

    /** Specifies all the public keys that require signatures for the transaction to be valid. */
    override val requiredSigningKeys: List<PublicKey>
        get() = ledgerTransaction.requiredSigningKeys

    /** Returns the same transaction but with an additional (unchecked) signature. */
    override fun withAdditionalSignature(sig: DigitalSignatureAndMetadata): SignedTransaction =
        SignedTransactionImpl(wireTransaction, sigs + sig)

    /** Returns the same transaction but with an additional (unchecked) signatures. */
    override fun withAdditionalSignatures(sigList: Iterable<DigitalSignatureAndMetadata>): SignedTransaction =
        SignedTransactionImpl(wireTransaction, sigs + sigList)
}