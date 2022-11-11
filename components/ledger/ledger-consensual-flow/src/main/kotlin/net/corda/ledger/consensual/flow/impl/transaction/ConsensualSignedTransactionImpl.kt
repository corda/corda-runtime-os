package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey
import java.util.Objects

class ConsensualSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val transactionSigningService: TransactionSignatureService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
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

    override val id: SecureHash
        get() = wireTransaction.id

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializationService)

    @Suspendable
    override fun addSignature(publicKey: PublicKey): Pair<ConsensualSignedTransaction, DigitalSignatureAndMetadata> {
        val newSignature = transactionSigningService.sign(id, publicKey)
        return Pair(
            ConsensualSignedTransactionImpl(
                serializationService,
                transactionSigningService,
                digitalSignatureVerificationService,
                wireTransaction,
            signatures + newSignature
            ),
            newSignature
        )
    }

    override fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransaction =
        ConsensualSignedTransactionImpl(serializationService, transactionSigningService, digitalSignatureVerificationService,
            wireTransaction, signatures + signature)

    override fun getMissingSignatories(): Set<PublicKey> {
        val appliedSignatories = signatures.filter{
            try {
                // TODO Signature spec to be determined internally by crypto code
                val signedData = SignableData(id, it.metadata)
                digitalSignatureVerificationService.verify(
                    publicKey = it.by,
                    signatureSpec = SignatureSpec.ECDSA_SHA256,
                    signatureData = it.signature.bytes,
                    clearData = serializationService.serialize(signedData).bytes
                )
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

    override fun verifySignatures() {
        val appliedSignatories = signatures.filter{
            try {
                // TODO Signature spec to be determined internally by crypto code
                val signedData = SignableData(id, it.metadata)
                digitalSignatureVerificationService.verify(
                    publicKey = it.by,
                    signatureSpec = SignatureSpec.ECDSA_SHA256,
                    signatureData = it.signature.bytes,
                    clearData = serializationService.serialize(signedData).bytes
                )
                true
            } catch (e: Exception) {
                throw TransactionVerificationException(id,
                    "Failed to verify signature of ${it.signature}. " +
                            "Message: ${e.message}", e
                )
            }
        }.map { it.by }.toSet()
        val requiredSignatories = this.toLedgerTransaction().requiredSignatories
        if (requiredSignatories.any {
            !it.isFulfilledBy(appliedSignatories) // isFulfilledBy() helps to make this working with CompositeKeys.
        }){
            throw TransactionVerificationException(id, "There are missing signatures", null)
        }
    }
}