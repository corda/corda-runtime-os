package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

data class UtxoSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,

    private val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
) : UtxoSignedTransaction {

    init {
        require(signatures.isNotEmpty()) { "Tried to instantiate a ${javaClass.simpleName} without any signatures." }
    }

    override val id: SecureHash get() = wireTransaction.id

    override fun addSignatures(signatures: Iterable<DigitalSignatureAndMetadata>): UtxoSignedTransaction {
        return copy(signatures = this.signatures + signatures)
    }

    override fun addSignatures(vararg signatures: DigitalSignatureAndMetadata): UtxoSignedTransaction {
        return addSignatures(signatures.toList())
    }

    override fun getMissingSignatories(): List<PublicKey> {
        val appliedSignatories = signatures.map { it.by }.toSet()
        val requiredSignatories = toLedgerTransaction().signatories
        return requiredSignatories.filter { !it.isFulfilledBy(appliedSignatories) }
    }

    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return UtxoLedgerTransactionImpl(wireTransaction, serializationService)
    }
}
