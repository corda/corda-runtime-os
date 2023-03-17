package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
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
    private val transactionSignatureService: TransactionSignatureService,
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    override val wireTransaction: WireTransaction,
    private val signatures: List<DigitalSignatureAndMetadata>
) : UtxoSignedTransactionInternal {

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
            transactionSignatureService,
            utxoLedgerTransactionFactory,
            wireTransaction,
            signatures + signature
        )

    @Suspendable
    override fun addMissingSignatures(): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
        val newSignatures = try {
            transactionSignatureService.sign(this, getMissingSignatories())
        } catch (_: TransactionNoAvailableKeysException) { // No signatures are needed if no keys are available.
            return Pair(this, emptyList())
        }
        return Pair(
            UtxoSignedTransactionImpl(
                serializationService,
                transactionSignatureService,
                utxoLedgerTransactionFactory,
                wireTransaction,
                signatures + newSignatures
            ),
            newSignatures
        )
    }

    private fun getSignatoryPublicKeyByKeyId(keyId: SecureHash): PublicKey? {
        val keyIdToSignatory = signatories.associateBy {// todo cache this
            transactionSignatureService.getIdOfPublicKey(
                it,
                keyId.algorithm
            )
        }
        return keyIdToSignatory[keyId]
    }

    // Against signatories. Notary/Unknown signatures are ignored.
    @Suspendable // TODO: are these need to be suspendable?
    override fun getMissingSignatories(): Set<PublicKey> {
        val appliedSignatories = signatures.mapNotNull {
            val publicKey = getSignatoryPublicKeyByKeyId(it.by)
            if (publicKey == null) {
                null
            } else {
                try {
                    transactionSignatureService.verifySignature(this, it, publicKey)
                    publicKey
                } catch (e: Exception) {
                    null
                }
            }
        }.toSet()

        // isFulfilledBy() helps to make this working with CompositeKeys.
        return signatories.filterNot { KeyUtils.isKeyFulfilledBy(it, appliedSignatories) }.toSet()
    }

    // Against signatories. Notary/unknown signatures are ignored
    @Suspendable
    override fun verifySignatorySignatures() {
        val appliedSignatories = signatures.mapNotNull {
            val publicKey = getSignatoryPublicKeyByKeyId(it.by)
            if (publicKey == null) {
                null// We do not care about non-notary/non-signatory keys
            } else {
                try {
                    transactionSignatureService.verifySignature(this, it, publicKey)
                    publicKey
                } catch (e: Exception) {
                    throw TransactionSignatureException(
                        id,
                        "Failed to verify signature of ${it.signature} for transaction $id. Message: ${e.message}",
                        e
                    )
                }
            }
        }.toSet()

        // isFulfilledBy() helps to make this working with CompositeKeys.
        val missingSignatories = signatories.filterNot { KeyUtils.isKeyFulfilledBy(it, appliedSignatories) }.toSet()
        if (missingSignatories.isNotEmpty()) {
            throw TransactionMissingSignaturesException(
                id,
                missingSignatories,
                "Transaction $id is missing signatures for signatories (encoded) ${missingSignatories.map { it.encoded }}"
            )
        }
    }

    private fun getNotaryPublicKeyByKeyId(keyId: SecureHash): PublicKey? {
        val keyIdNotary = getNotaryKeys().associateBy {// todo cache this
            transactionSignatureService.getIdOfPublicKey(
                it,
                keyId.algorithm
            )
        }
        return keyIdNotary[keyId]
    }

    @Suspendable
    override fun verifyAttachedNotarySignature() {
        val notaryPublicKeysWithValidSignatures = signatures.mapNotNull {
            val publicKey = getNotaryPublicKeyByKeyId(it.by)
            if (publicKey != null) {
                try {
                    transactionSignatureService.verifySignature(this, it, publicKey)
                    publicKey
                } catch (e: Exception) {
                    throw TransactionSignatureException(
                        id,
                        "Failed to verify signature of ${it.signature} for transaction $id. Message: ${e.message}",
                        e
                    )
                }
            } else {
                null
            }
        }
        // If the notary service key (composite key) is provided we need to make sure it contains the key the
        // transaction was signed with. This means it was signed with one of the notary VNodes (worker).
        if (!KeyUtils.isKeyInSet(
                notaryKey,
                notaryPublicKeysWithValidSignatures
            )
        ) {
            throw TransactionSignatureException(
                id,
                "There are no notary signatures attached to the transaction.",
                null
            )
        }
    }

    @Suspendable
    override fun verifyNotarySignature(signature: DigitalSignatureAndMetadata) {
        val publicKey = getNotaryPublicKeyByKeyId(signature.by)
            ?: throw CordaRuntimeException( // todo transition to TransactionSignatureException
                "Notary's signature has not been created by the transaction's notary. " +
                        "Notary's public key: ${notary.owningKey} " +
                        "Notary signature's key Id: ${signature.by}"
            )

        if (!KeyUtils.isKeyInSet(notary.owningKey, listOf(publicKey))) {
            throw CordaRuntimeException( // todo transition to TransactionSignatureException
                "Notary's signature has not been created by the transaction's notary. " +
                        "Notary's public key: ${notary.owningKey} " +
                        "Notary signature's key: $publicKey"
            )
        }
        try {
            transactionSignatureService.verifySignature(this, signature, publicKey)
        } catch (e: Exception) {
            throw TransactionSignatureException(
                id,
                "Failed to verify notary signature of ${signature.signature} for transaction $id. Message: ${e.message}",
                e
            )
        }
    }

    @Suspendable
    override fun verifySignatorySignature(signature: DigitalSignatureAndMetadata) {
        val publicKey = getSignatoryPublicKeyByKeyId(signature.by)
            ?: return // We do not care about non-notary/non-signatory signatures.

        try {
            transactionSignatureService.verifySignature(this, signature, publicKey)
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

    private fun getNotaryKeys(): List<PublicKey> {
        return when (val owningKey = notary.owningKey) {
            is CompositeKey -> owningKey.leafKeys.toList()
            else -> listOf(owningKey)
        }
    }
}
