package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey
import java.util.Objects

data class UtxoSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val transactionSignatureService: TransactionSignatureService,

    override val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
) : UtxoSignedTransactionInternal {

    init {
        require(signatures.isNotEmpty()) { "Tried to instantiate a ${javaClass.simpleName} without any signatures." }
        // TODO(CORE-7237 Check WireTx's metadata's ledger type and allow only the matching ones.)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoSignedTransactionImpl) return false
        if (other.wireTransaction != wireTransaction) return false
        if (other.signatures.size != signatures.size) return false

        return other.signatures.withIndex().all{
            it.value == signatures[it.index]
        }
    }

    override fun hashCode(): Int = Objects.hash(wireTransaction, signatures)

    override val id: SecureHash get() = wireTransaction.id

    @Suspendable
    override fun sign(publicKey: PublicKey): Pair<UtxoSignedTransactionInternal, DigitalSignatureAndMetadata> {
        val newSignature = transactionSignatureService.sign(id, publicKey)
        return Pair(
            UtxoSignedTransactionImpl(
                serializationService,
                transactionSignatureService,
                wireTransaction,
                signatures + newSignature
            ),
            newSignature
        )
    }

    override fun addSignature(signature: DigitalSignatureAndMetadata): UtxoSignedTransactionInternal =
        UtxoSignedTransactionImpl(serializationService, transactionSignatureService,
            wireTransaction, signatures + signature)

    @Suspendable
    override fun addMissingSignatures(): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>>{
        TODO("Not implemented yet")
    }

    override fun getMissingSignatories(): Set<PublicKey> {
        val appliedSignatories = signatures.filter{
            try {
                transactionSignatureService.verifySignature(id, it)
                true
            } catch (e: Exception) {
                false
            }
        }.map { it.by }.toSet()
        val requiredSignatories = toLedgerTransaction().signatories
        return requiredSignatories.filter {
            !it.isFulfilledBy(appliedSignatories) // isFulfilledBy() helps to make this working with CompositeKeys.
        }.toSet()
    }

    override fun verifySignatures() {
        val appliedSignatories = signatures.filter{
            try {
                transactionSignatureService.verifySignature(id, it)
                true
            } catch (e: Exception) {
                throw TransactionVerificationException(id,
                    "Failed to verify signature of ${it.signature}. " +
                            "Message: ${e.message}", e
                )
            }
        }.map { it.by }.toSet()
        val requiredSignatories = this.toLedgerTransaction().signatories
        if (requiredSignatories.any {
                !it.isFulfilledBy(appliedSignatories) // isFulfilledBy() helps to make this working with CompositeKeys.
            }){
            throw TransactionVerificationException(id, "There are missing signatures", null)
        }
    }
    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return UtxoLedgerTransactionImpl(wireTransaction, serializationService)
    }
}
