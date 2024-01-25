package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey
import java.util.Objects

@Suppress("TooManyFunctions")
data class UtxoSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val transactionSignatureServiceInternal: TransactionSignatureServiceInternal,
    private val notarySignatureVerificationService: NotarySignatureVerificationServiceInternal,
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    override val wireTransaction: WireTransaction,
    private val signatures: List<DigitalSignatureAndMetadata>
) : UtxoSignedTransactionInternal {

    private val keyIdToSignatories: MutableMap<String, Map<SecureHash, PublicKey>> = mutableMapOf()
    private val keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>> = mutableMapOf()

    init {
        require(signatures.isNotEmpty()) { "Tried to instantiate a ${javaClass.simpleName} without any signatures." }
        verifyMetadata(wireTransaction.metadata)
    }

    private val wrappedWireTransaction = WrappedUtxoWireTransaction(wireTransaction, serializationService)

    override fun getId(): SecureHash {
        return wireTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        return wireTransaction.metadata
    }

    override fun getSignatures(): List<DigitalSignatureAndMetadata> {
        return signatures
    }

    override fun getInputStateRefs(): List<StateRef> {
        return wrappedWireTransaction.inputStateRefs
    }

    override fun getReferenceStateRefs(): List<StateRef> {
        return wrappedWireTransaction.referenceStateRefs
    }

    override fun getOutputStateAndRefs(): List<StateAndRef<*>> {
        return wrappedWireTransaction.outputStateAndRefs
    }

    override fun getNotaryName(): MemberX500Name {
        return wrappedWireTransaction.notaryName
    }

    override fun getNotaryKey(): PublicKey {
        return wrappedWireTransaction.notaryKey
    }

    override fun getTimeWindow(): TimeWindow {
        return wrappedWireTransaction.timeWindow
    }

    override fun getCommands(): List<Command> {
        return wrappedWireTransaction.commands
    }

    override fun getSignatories(): List<PublicKey> {
        return wrappedWireTransaction.signatories
    }

    override fun addSignature(signature: DigitalSignatureAndMetadata): UtxoSignedTransactionInternal =
        UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureServiceInternal,
            notarySignatureVerificationService,
            utxoLedgerTransactionFactory,
            wireTransaction,
            signatures + signature
        )

    @Suspendable
    override fun addMissingSignatures(): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
        val newSignatures = try {
            transactionSignatureServiceInternal.sign(this, getMissingSignatories())
        } catch (_: TransactionNoAvailableKeysException) { // No signatures are needed if no keys are available.
            return Pair(this, emptyList())
        }
        return Pair(
            UtxoSignedTransactionImpl(
                serializationService,
                transactionSignatureServiceInternal,
                notarySignatureVerificationService,
                utxoLedgerTransactionFactory,
                wireTransaction,
                signatures + newSignatures
            ),
            newSignatures
        )
    }

    private fun getSignatoryKeyFromKeyId(keyId: SecureHash): PublicKey? {
        val keyIdToPublicKey = keyIdToSignatories.getOrPut(keyId.algorithm) {
            // Prepare keyIds for all public keys related to signatories for the relevant algorithm
            signatories.flatMap { signatory ->
                notarySignatureVerificationService.getKeyOrLeafKeys(signatory).map {
                    transactionSignatureServiceInternal.getIdOfPublicKey(
                        it, keyId.algorithm
                    ) to it
                }
            }.toMap()
        }
        return keyIdToPublicKey[keyId]
    }

    // Notary/unknown signatures are ignored.
    override fun getMissingSignatories(): Set<PublicKey> {
        return getMissingSignatories(getPublicKeysToSignatorySignatures())
    }

    // Notary/unknown signatures are ignored
    override fun verifySignatorySignatures() {
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
                transactionSignatureServiceInternal.verifySignature(this, signature, publicKey)
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
        return signatories
            .filterNot { KeyUtils.isKeyFulfilledBy(it, publicKeysWithSignatures) }
            .toSet()
    }

    private fun getPublicKeysToSignatorySignatures(): Map<PublicKey, DigitalSignatureAndMetadata> {
        return signatures.mapNotNull { // We do not care about non-notary/non-signatory keys
            (getSignatoryKeyFromKeyId(it.by) ?: return@mapNotNull null) to it
        }.toMap()
    }

    override fun verifyAttachedNotarySignature() {
        notarySignatureVerificationService.verifyNotarySignatures(this, notaryKey, signatures, keyIdToNotaryKeys)
    }

    override fun verifyNotarySignature(signature: DigitalSignatureAndMetadata) {
        val publicKey = notarySignatureVerificationService.getNotaryPublicKeyByKeyId(signature.by, notaryKey, keyIdToNotaryKeys)
            ?: throw TransactionSignatureException(
                id,
                "Notary signature has not been created by the notary for this transaction. " +
                    "Notary public key: $notaryKey " +
                    "Notary signature key Id: ${signature.by}",
                null
            )

        try {
            transactionSignatureServiceInternal.verifySignature(this, signature, publicKey)
        } catch (e: Exception) {
            throw TransactionSignatureException(
                id,
                "Failed to verify notary signature of ${signature.signature} for transaction $id. Message: ${e.message}",
                e
            )
        }
    }

    override fun verifySignatorySignature(signature: DigitalSignatureAndMetadata) {
        val publicKey = getSignatoryKeyFromKeyId(signature.by)
            ?: return // We do not care about non-notary/non-signatory signatures.

        try {
            transactionSignatureServiceInternal.verifySignature(this, signature, publicKey)
        } catch (e: Exception) {
            throw TransactionSignatureException(
                id,
                "Failed to verify signature of ${signature.signature} for transaction $id. Message: ${e.message}",
                e
            )
        }
    }

    @Suspendable
    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return utxoLedgerTransactionFactory.create(wireTransaction)
    }

    override fun toLedgerTransaction(
        inputStateAndRefs: List<StateAndRef<*>>,
        referenceStateAndRefs: List<StateAndRef<*>>
    ): UtxoLedgerTransaction {
        return utxoLedgerTransactionFactory.createWithStateAndRefs(
            wireTransaction,
            inputStateAndRefs,
            referenceStateAndRefs
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoSignedTransactionImpl) return false
        if (other.wireTransaction != wireTransaction) return false
        if (other.signatures.size != signatures.size) return false

        return other.signatures.withIndex().all {
            it.value == signatures[it.index]
        }
    }

    override fun hashCode(): Int = Objects.hash(wireTransaction, signatures)

    override fun toString(): String {
        return "UtxoSignedTransactionImpl(id=$id, signatures=$signatures, wireTransaction=$wireTransaction)"
    }
}
