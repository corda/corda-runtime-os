package net.corda.ledger.utxo.impl.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

data class UtxoSignedTransactionImpl(
    override val id: SecureHash,
    override val signatures: List<DigitalSignatureAndMetadata>
) : UtxoSignedTransaction {

    override fun addSignatures(signatures: Iterable<DigitalSignatureAndMetadata>): UtxoSignedTransaction {
        return copy(signatures = this.signatures + signatures)
    }

    override fun addSignatures(vararg signatures: DigitalSignatureAndMetadata): UtxoSignedTransaction {
        return addSignatures(signatures.toList())
    }

    override fun getMissingSignatories(): List<PublicKey> {
        TODO("Not yet implemented")
    }

    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        TODO("Not yet implemented")
    }
}
