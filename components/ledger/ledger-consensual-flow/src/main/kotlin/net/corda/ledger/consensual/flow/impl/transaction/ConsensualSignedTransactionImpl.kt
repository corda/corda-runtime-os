package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.util.Objects

class ConsensualSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val transactionSignatureService: TransactionSignatureService,
    override val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
): ConsensualSignedTransactionInternal
{
    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
        // TODO(CORE-7237 Check WireTx's metadata's ledger type and allow only the matching ones.)
    }

    override val id: SecureHash
        get() = wireTransaction.id

    override val metadata: TransactionMetadata
        get() = wireTransaction.metadata

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializationService)

    override fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransactionInternal =
        ConsensualSignedTransactionImpl(serializationService, transactionSignatureService,
            wireTransaction, signatures + signature)

    @Suspendable
    override fun addMissingSignatures(): Pair<ConsensualSignedTransactionInternal, List<DigitalSignatureAndMetadata>>{
        val newSignatures = transactionSignatureService.sign(id, getMissingSignatories())
        return Pair(
            ConsensualSignedTransactionImpl(
                serializationService,
                transactionSignatureService,
                wireTransaction,
                signatures + newSignatures
            ),
            newSignatures
        )
    }

    override fun getMissingSignatories(): Set<PublicKey> {
        val appliedSignatories = signatures.filter{
            try {
                transactionSignatureService.verifySignature(this, it)
                true
            } catch (e: Exception) {
                false
            }
        }.map { it.by }.toSet()
        val requiredSignatories = this.toLedgerTransaction().requiredSignatories
        return requiredSignatories.filter {
            !it.isFulfilledBy(appliedSignatories) // isFulfilledBy() helps to make this working with CompositeKeys.
        }.toSet()
    }

    @Suspendable
    override fun verifySignatures() {
        val appliedSignatories = signatures.filter{
            try {
                transactionSignatureService.verifySignature(this, it)
                true
            } catch (e: Exception) {
                throw TransactionVerificationException(
                    id,
                    "Failed to verify signature of ${it.signature} for transaction $id. Message: ${e.message}",
                    e
                )
            }
        }.map { it.by }.toSet()

        // isFulfilledBy() helps to make this working with CompositeKeys.
        val missingSignatories = toLedgerTransaction()
            .requiredSignatories
            .filterNot { it.isFulfilledBy(appliedSignatories) }
            .toSet()
        if (missingSignatories.isNotEmpty()) {
            throw TransactionMissingSignaturesException(
                id,
                missingSignatories,
                "Transaction $id is missing signatures for signatories (encoded) ${missingSignatories.map { it.encoded }}"
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualSignedTransactionImpl) return false
        if (other.wireTransaction != wireTransaction) return false
        if (other.signatures.size != signatures.size) return false

        return other.signatures.withIndex().all{
            it.value == signatures[it.index]
        }
    }

    override fun hashCode(): Int = Objects.hash(wireTransaction, signatures)

    override fun toString(): String {
        return "ConsensualSignedTransactionImpl(id=$id, signatures=$signatures, wireTransaction=$wireTransaction)"
    }


}