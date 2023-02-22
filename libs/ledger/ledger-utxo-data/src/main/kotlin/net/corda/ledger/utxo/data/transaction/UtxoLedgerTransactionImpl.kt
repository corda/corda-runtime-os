package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.state.filterIsContractStateInstance
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

@Suppress("TooManyFunctions")
class UtxoLedgerTransactionImpl(
    private val wrappedWireTransaction: WrappedUtxoWireTransaction,
    private val inputStateAndRefs: List<StateAndRef<*>>,
    private val referenceStateAndRefs: List<StateAndRef<*>>
) : UtxoLedgerTransactionInternal {

    init {
        verifyMetadata(wireTransaction.metadata)
    }

    override val wireTransaction: WireTransaction
        get() = wrappedWireTransaction.wireTransaction

//    val attachmentIds: List<SecureHash>
//        get() = wrappedWireTransaction.attachmentIds

    override fun getId(): SecureHash {
        return wrappedWireTransaction.id
    }

    override fun getNotary(): Party {
        return wrappedWireTransaction.notary
    }

    override fun getMetadata(): TransactionMetadata {
        return wrappedWireTransaction.metadata
    }

    override fun getTimeWindow(): TimeWindow {
        return wrappedWireTransaction.timeWindow
    }

    override fun getSignatories(): List<PublicKey> {
        return wrappedWireTransaction.signatories
    }

    override fun getAttachments(): List<Attachment> {
        return wrappedWireTransaction.attachments
    }

    override fun getAttachment(id: SecureHash): Attachment {
        return requireNotNull(attachments.singleOrNull { it.id == id }) {
            "Failed to find a single attachment with id: $id."
        }
    }

    override fun getCommands(): List<Command> {
        return wrappedWireTransaction.commands
    }

    override fun <T : Command?> getCommands(type: Class<T>): List<T> {
        return commands.filterIsInstance(type)
    }

    override fun getInputStateRefs(): List<StateRef> {
        return wrappedWireTransaction.inputStateRefs
    }

    override fun getInputStateAndRefs(): List<StateAndRef<*>> {
        return inputStateAndRefs
    }

    override fun <T : ContractState> getInputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return inputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getInputStates(type: Class<T>): List<T> {
        return inputContractStates.filterIsInstance(type)
    }

    override fun getReferenceStateRefs(): List<StateRef> {
        return wrappedWireTransaction.referenceStateRefs
    }

    override fun getReferenceStateAndRefs(): List<StateAndRef<*>> {
        return referenceStateAndRefs
    }

    override fun <T : ContractState> getReferenceStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return referenceStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getReferenceStates(type: Class<T>): List<T> {
        return referenceContractStates.filterIsInstance(type)
    }

    override fun getOutputStateAndRefs(): List<StateAndRef<*>> {
        return wrappedWireTransaction.outputStateAndRefs
    }

    override fun <T : ContractState> getOutputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return outputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getOutputStates(type: Class<T>): List<T> {
        return outputContractStates.filterIsInstance(type)
    }

    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoLedgerTransactionImpl
                && other.wrappedWireTransaction == wrappedWireTransaction
    }

    override fun hashCode(): Int {
        return wrappedWireTransaction.hashCode()
    }

    override fun toString(): String {
        return "UtxoLedgerTransactionImpl(id=$id, wireTransaction=${wrappedWireTransaction.wireTransaction})"
    }
}