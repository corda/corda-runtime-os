package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.verifier.verifyMetadata
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.util.Objects

class ConsensualSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val transactionSignatureService: TransactionSignatureServiceInternal,
    override val wireTransaction: WireTransaction,
    private val signatures: List<DigitalSignatureAndMetadata>
) : ConsensualSignedTransactionInternal {

    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
        verifyMetadata(wireTransaction.metadata)
    }

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializationService)

    override fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransactionInternal =
        ConsensualSignedTransactionImpl(
            serializationService, transactionSignatureService,
            wireTransaction, signatures + signature
        )

    @Suspendable
    override fun addMissingSignatures(): Pair<ConsensualSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
        val newSignatures = try {
            transactionSignatureService.sign(this, getMissingSignatories())
        } catch (_: TransactionNoAvailableKeysException) { // No signatures are needed if no keys are available.
            return Pair(this, emptyList())
        }
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

    private fun getSignatoryKeyFromKeyId(keyId: SecureHash, signatoriesKeys: Set<PublicKey>): PublicKey? {
        val keyIdsToSignatories = signatoriesKeys.associateBy {// todo cache this
            transactionSignatureService.getIdOfPublicKey(
                it,
                keyId.algorithm
            )
        }
        return keyIdsToSignatories[keyId]
    }

    override fun getMissingSignatories(): Set<PublicKey> {
        // TODO See if adding the following as property breaks CordaSerializable
        val requiredSignatories = this.toLedgerTransaction().requiredSignatories
        val signatoriesWithValidSignatures = signatures.mapNotNull {
            val signatureKey = getSignatoryKeyFromKeyId(it.by, requiredSignatories)
            if (signatureKey == null) {
                null
            } else {
                try {
                    transactionSignatureService.verifySignature(this, it, signatureKey)
                    signatureKey
                } catch (e: Exception) {
                    null
                }
            }
        }.toSet()

        // isKeyFulfilledBy() helps to make this working with CompositeKeys.
        return requiredSignatories.filterNot { KeyUtils.isKeyFulfilledBy(it, signatoriesWithValidSignatures) }.toSet()
    }

    override fun verifySignatures() {
        // TODO See if adding the following as property breaks CordaSerializable
        val requiredSignatories = this.toLedgerTransaction().requiredSignatories
        val signatoriesWithValidSignatures = signatures.mapNotNull {
            val signatureKey = getSignatoryKeyFromKeyId(it.by, requiredSignatories)
            if (signatureKey == null) {
                null
            } else {
                try {
                    transactionSignatureService.verifySignature(this, it, signatureKey)
                    signatureKey
                } catch (e: Exception) {
                    throw TransactionSignatureException(
                        id,
                        "Failed to verify signature of ${it.signature} for transaction $id. Message: ${e.message}",
                        e
                    )
                }
            }
        }.toSet()

        // isKeyFulfilledBy() helps to make this working with CompositeKeys.
        val missingSignatories = requiredSignatories.filterNot { KeyUtils.isKeyFulfilledBy(it, signatoriesWithValidSignatures) }.toSet()
        if (missingSignatories.isNotEmpty()) {
            throw TransactionMissingSignaturesException(
                id,
                missingSignatories,
                "Transaction $id is missing signatures for signatories (encoded) ${missingSignatories.map { it.encoded }}"
            )
        }
    }

    override fun verifySignature(signature: DigitalSignatureAndMetadata) {
        // TODO See if adding the following as property breaks CordaSerializable
        val requiredSignatories = this.toLedgerTransaction().requiredSignatories
        val publicKey = getSignatoryKeyFromKeyId(signature.by, requiredSignatories)
            ?: return

        try {
            transactionSignatureService.verifySignature(this, signature, publicKey)
        } catch (e: Exception) {
            throw TransactionSignatureException(
                id,
                "Failed to verify signature of ${signature.signature} from $publicKey for transaction $id. Message: ${e.message}",
                e
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualSignedTransactionImpl) return false
        if (other.wireTransaction != wireTransaction) return false
        if (other.signatures.size != signatures.size) return false

        return other.signatures.withIndex().all {
            it.value == signatures[it.index]
        }
    }

    override fun hashCode(): Int = Objects.hash(wireTransaction, signatures)

    override fun toString(): String {
        return "ConsensualSignedTransactionImpl(id=$id, signatures=$signatures, wireTransaction=$wireTransaction)"
    }

    override fun getId(): SecureHash {
        return wireTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        return wireTransaction.metadata
    }

    override fun getSignatures(): List<DigitalSignatureAndMetadata> {
        return signatures
    }
}
