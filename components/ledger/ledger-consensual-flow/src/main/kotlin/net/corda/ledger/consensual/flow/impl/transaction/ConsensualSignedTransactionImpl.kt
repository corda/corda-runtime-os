package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.verifier.verifyMetadata
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.util.Objects

@Suppress("TooManyFunctions")
class ConsensualSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val transactionSignatureService: TransactionSignatureServiceInternal,
    override val wireTransaction: WireTransaction,
    private val signatures: List<DigitalSignatureAndMetadata>
) : ConsensualSignedTransactionInternal {

    private val keyIdToSignatories: MutableMap<String, Map<SecureHash, PublicKey>> = mutableMapOf()
    private val requiredSignatories: Set<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        toLedgerTransaction().requiredSignatories
    }

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
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signatures + signature
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

    private fun getSignatoryKeyFromKeyId(keyId: SecureHash): PublicKey? {
        val keyIdToPublicKey = keyIdToSignatories.getOrPut(keyId.algorithm) {
            // Prepare keyIds for all public keys related to signatories for the relevant algorithm
            requiredSignatories.map { signatory ->
                getKeyOrLeafKeys(signatory).map {
                    transactionSignatureService.getIdOfPublicKey(
                        it, keyId.algorithm
                    ) to it
                }
            }.flatten().toMap()
        }
        return keyIdToPublicKey[keyId]
    }

    // Unknown signatures are ignored
    override fun getMissingSignatories(): Set<PublicKey> {
        return getMissingSignatories(getPublicKeysToSignatorySignatures())
    }

    // Unknown signatures are ignored
    override fun verifySignatures() {
        val publicKeysToSignatures =
            getPublicKeysToSignatorySignatures()

        val missingSignatories = getMissingSignatories(publicKeysToSignatures)
        if (missingSignatories.isNotEmpty()) {
            throw TransactionMissingSignaturesException(
                id,
                missingSignatories,
                "Transaction $id is missing signatures for signatories (encoded) ${
                    missingSignatories.map { it.encoded }
                }"
            )
        }
        publicKeysToSignatures.forEach { (publicKey, signature) ->
            try {
                transactionSignatureService.verifySignature(this, signature, publicKey)
            } catch (e: Exception) {
                throw TransactionSignatureException(
                    id,
                    "Failed to verify signature of $signature from $publicKey for transaction $id. Message: ${e.message}",
                    e
                )
            }
        }
    }

    private fun getMissingSignatories(publicKeysToSignatures: Map<PublicKey, DigitalSignatureAndMetadata>): Set<PublicKey> {
        val publicKeysWithSignatures = publicKeysToSignatures.keys.toHashSet()

        // TODO CORE-12207 isKeyFulfilledBy is not the most efficient
        // isKeyFulfilledBy() helps to make this working with CompositeKeys.
        return requiredSignatories
            .filterNot { KeyUtils.isKeyFulfilledBy(it, publicKeysWithSignatures) }
            .toSet()
    }

    private fun getPublicKeysToSignatorySignatures(): Map<PublicKey, DigitalSignatureAndMetadata> {
        return signatures.mapNotNull { // We do not care about non-notary/non-signatory keys
            (getSignatoryKeyFromKeyId(it.by) ?: return@mapNotNull null) to it
        }
            .toMap()
    }

    override fun verifySignature(signature: DigitalSignatureAndMetadata) {
        val publicKey = getSignatoryKeyFromKeyId(signature.by)
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

    private fun getKeyOrLeafKeys(publicKey: PublicKey): List<PublicKey> {
        return when (publicKey) {
            is CompositeKey -> publicKey.leafKeys.toList()
            else -> listOf(publicKey)
        }
    }
}
