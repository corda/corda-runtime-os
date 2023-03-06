package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
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

    override fun getNotary(): Party {
        return wrappedWireTransaction.notary
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

    override fun getMissingSignatories(): Set<PublicKey> {
        val appliedSignatories = signatures.filter {
            try {
                transactionSignatureService.verifySignature(this, it)
                true
            } catch (e: Exception) {
                false
            }
        }.map { it.by }.toSet()

        // isFulfilledBy() helps to make this working with CompositeKeys.
        return signatories.filterNot { KeyUtils.isKeyFulfilledBy(it, appliedSignatories) }.toSet()
    }

    @Suspendable
    override fun verifySignatures() {
        val appliedSignatories = signatures.filter {
            try {
                transactionSignatureService.verifySignature(this, it)
                true
            } catch (e: Exception) {
                throw TransactionSignatureException(
                    id,
                    "Failed to verify signature of ${it.signature} for transaction $id. Message: ${e.message}",
                    e
                )
            }
        }.map { it.by }.toSet()

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

    @Suspendable
    override fun verifyNotarySignatureAttached() {
        if (!KeyUtils.isKeyFulfilledBy(notary.owningKey, signatures.map { it.by })) {
            throw TransactionSignatureException(
                id,
                "There are no notary signatures attached to the transaction.",
                null
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
}
