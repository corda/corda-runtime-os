package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures

class UtxoSignedTransactionWithDependencies(
    val delegate: UtxoSignedTransactionInternal,
    val filteredDependencies: List<UtxoFilteredTransactionAndSignatures>
) : UtxoSignedTransactionInternal by delegate {

    @Suspendable
    override fun addSignature(signature: DigitalSignatureAndMetadata): UtxoSignedTransactionWithDependencies {
        return UtxoSignedTransactionWithDependencies(
            delegate.addSignature(signature),
            filteredDependencies
        )
    }

    @Suspendable
    override fun addMissingSignatures(): Pair<UtxoSignedTransactionWithDependencies, List<DigitalSignatureAndMetadata>> {
        val (transaction, signatures) = delegate.addMissingSignatures()
        return UtxoSignedTransactionWithDependencies(
            transaction,
            filteredDependencies
        ) to signatures
    }
}
