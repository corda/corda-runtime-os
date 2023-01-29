package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.filterIsContractStateInstance
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.MessageDigest
import java.security.PublicKey

@CordaSerializable
data class UtxoLedgerTransactionBase(
    override val attachments: List<Attachment>,
    override val commands: List<Command>,
    override val inputStateAndRefs: List<StateAndRef<*>>,
    override val inputStateRefs: List<StateRef>,
    override val referenceStateAndRefs: List<StateAndRef<*>>,
    override val referenceStateRefs: List<StateRef>,
    override val signatories: List<PublicKey>,
    override val timeWindow: TimeWindow,
    override val outputContractStates : List<ContractState>,
    val notary: Party
) : UtxoLedgerTransaction {

    val bytes: ByteArray by lazy {
        val serializer = BaseSerializationService()
        serializer.serialize(this).bytes
    }

    override val id: SecureHash by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        SecureHash(digest.algorithm, digest.digest(bytes))
    }

    //TODO Implement encumbrance
    override val outputStateAndRefs: List<StateAndRef<*>>
        get() {
            return outputContractStates.mapIndexed { index, contractState ->
                val stateRef = StateRef(id, index)
                val transactionState = TransactionStateImpl(contractState, notary, null)
                StateAndRefImpl(transactionState, stateRef)
            }
        }

    override fun getAttachment(id: SecureHash): Attachment {
        TODO("Not yet implemented")
    }

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

}