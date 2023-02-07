package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.filterIsContractStateInstance
import net.corda.simulator.runtime.ledger.SimTransactionMetadata
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Objects

data class UtxoLedgerTransactionBase(
    val ledgerInfo: UtxoStateLedgerInfo,
    override val inputStateAndRefs: List<StateAndRef<*>>,
    override val referenceStateAndRefs: List<StateAndRef<*>>,
) : UtxoLedgerTransaction {

    val bytes: ByteArray by lazy {
        val serializer = BaseSerializationService()
        serializer.serialize(ledgerInfo.commands).bytes
            .plus(serializer.serialize(ledgerInfo.inputStateRefs).bytes)
            .plus(serializer.serialize(ledgerInfo.notary).bytes)
            .plus(serializer.serialize(ledgerInfo.referenceStateRefs).bytes)
            .plus(serializer.serialize(ledgerInfo.signatories).bytes)
            .plus(serializer.serialize(ledgerInfo.timeWindow).bytes)
            .plus(serializer.serialize(ledgerInfo.outputStates).bytes)
            .plus(serializer.serialize(ledgerInfo.attachments).bytes)
    }

    override val id: SecureHash by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        SecureHash(digest.algorithm, digest.digest(bytes))
    }

    //TODO Implement encumbrance
    override val outputStateAndRefs: List<StateAndRef<*>>
        get() {
            return ledgerInfo.outputStates.mapIndexed { index, contractState ->
                val stateRef = StateRef(id, index)
                val transactionState = TransactionStateImpl(contractState, ledgerInfo.notary, null)
                StateAndRefImpl(transactionState, stateRef)
            }
        }
    override val attachments: List<Attachment>
        get() = emptyList() // TODO Not yet Implemented
    override val commands: List<Command>
        get() = ledgerInfo.commands

    override val inputStateRefs: List<StateRef>
        get() = ledgerInfo.inputStateRefs
    override val metadata: TransactionMetadata
        get() =  SimTransactionMetadata()
    override val notary: Party
        get() = ledgerInfo.notary

    override fun getAttachment(id: SecureHash): Attachment {
        TODO("Not yet implemented")
    }
    override val referenceStateRefs: List<StateRef>
        get() = ledgerInfo.referenceStateRefs
    override val signatories: List<PublicKey>
        get() = ledgerInfo.signatories
    override val timeWindow: TimeWindow
        get() = ledgerInfo.timeWindow

    override fun <T : Command> getCommands(type: Class<T>): List<T> {
        return commands.filterIsInstance(type)
    }

    override fun <T : ContractState> getInputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return inputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getInputStates(type: Class<T>): List<T> {
        return inputContractStates.filterIsInstance(type)
    }

    override fun <T : ContractState> getReferenceStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return referenceStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getReferenceStates(type: Class<T>): List<T> {
        return referenceContractStates.filterIsInstance(type)
    }

    override fun <T : ContractState> getOutputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return outputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getOutputStates(type: Class<T>): List<T> {
        return outputContractStates.filterIsInstance(type)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UtxoLedgerTransactionBase
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

}

