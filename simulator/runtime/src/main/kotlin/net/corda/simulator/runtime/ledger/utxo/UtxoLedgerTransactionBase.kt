package net.corda.simulator.runtime.ledger.utxo

import com.fasterxml.jackson.annotation.JsonIgnore
import net.corda.crypto.core.SecureHashImpl
import net.corda.simulator.runtime.ledger.consensual.SimTransactionMetadata
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.GroupParameters
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Objects

/**
 * Simulator implementation of [UtxoLedgerTransaction]
 */
@Suppress("TooManyFunctions")
data class UtxoLedgerTransactionBase(
    val ledgerInfo: UtxoStateLedgerInfo,
    private val inputStateAndRefs: List<StateAndRef<*>>,
    private val referenceStateAndRefs: List<StateAndRef<*>>
) : UtxoLedgerTransaction {

    /**
     *  This is created by concatenating the serialized bytes of individual utxo ledger components.
     *  This is later used to derive the txId
     */
    val bytes: ByteArray by lazy {
        val serializer = BaseSerializationService()
        serializer.serialize(ledgerInfo.commands).bytes
            .plus(serializer.serialize(ledgerInfo.inputStateRefs).bytes)
            .plus(serializer.serialize(ledgerInfo.referenceStateRefs).bytes)
            .plus(serializer.serialize(ledgerInfo.signatories).bytes)
            .plus(serializer.serialize(ledgerInfo.timeWindow).bytes)
            .plus(serializer.serialize(ledgerInfo.outputStates).bytes)
            .plus(serializer.serialize(ledgerInfo.attachments).bytes)
    }

    override fun getAttachment(id: SecureHash): Attachment {
        return requireNotNull(attachments.singleOrNull { it.id == id }) {
            "Failed to find a single attachment with id: $id."
        }
    }

    override fun getCommands(): List<Command> {
        return ledgerInfo.commands
    }

    override fun <T : Command> getCommands(type: Class<T>): List<T> {
        return commands.filterIsInstance(type)
    }

    override fun getInputStateRefs(): List<StateRef> {
        return ledgerInfo.inputStateRefs
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
        return ledgerInfo.referenceStateRefs
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
        // Calculate encumbrance group size for each encumbrance tag
        val encumbranceGroupSizes =
            ledgerInfo.outputStates.mapNotNull { it.encumbranceTag }.groupingBy { it }.eachCount()

        //Convert output contract states to StateAndRef
        return ledgerInfo.outputStates.mapIndexed { index, contractStateAndTag ->
            val stateRef = StateRef(id, index)
            val transactionState = contractStateAndTag.toTransactionState(notaryName, notaryKey,
                contractStateAndTag.encumbranceTag?.let{tag -> encumbranceGroupSizes[tag]})
            SimStateAndRef(transactionState, stateRef)
        }
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

    override fun getId(): SecureHash {
        val digest = MessageDigest.getInstance("SHA-256")
        return SecureHashImpl(digest.algorithm, digest.digest(bytes))
    }

    override fun getNotaryName(): MemberX500Name {
        return ledgerInfo.notaryName
    }

    override fun getNotaryKey(): PublicKey {
        return ledgerInfo.notaryKey
    }

    override fun getMetadata(): TransactionMetadata {
        return SimTransactionMetadata()
    }

    override fun getTimeWindow(): TimeWindow {
        return ledgerInfo.timeWindow
    }

    override fun getSignatories(): List<PublicKey> {
        return ledgerInfo.signatories
    }

    override fun getAttachments(): List<Attachment> {
        return emptyList() // TODO Not yet Implemented
    }

    @JsonIgnore
    override fun getGroupParameters(): GroupParameters {
        TODO("Not implemented")
    }
}

/**
 * Filters a collection of [StateAndRef] and returns only the elements that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast to.
 * @return Returns a collection of [StateAndRef] that match the specified [ContractState] type.
 */
fun <T : ContractState> Iterable<StateAndRef<*>>.filterIsContractStateInstance(type: Class<T>): List<StateAndRef<T>> {
    return filter { type.isAssignableFrom(it.state.contractState.javaClass) }.map { it.cast(type) }
}

/**
 * Casts the current [ContractState] to the specified type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast.
 * @return Returns a new [ContractState] instance cast to the specified type.
 * @throws IllegalArgumentException if the current [ContractState] cannot be cast to the specified type.
 */
fun <T : ContractState> ContractState.cast(type: Class<T>): T {
    return if (type.isAssignableFrom(javaClass)) {
        type.cast(this)
    } else {
        throw IllegalArgumentException("ContractState of type ${javaClass.canonicalName} cannot be cast to type ${type.canonicalName}.")
    }
}

/**
 * Casts the current [TransactionState] to the specified type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast to.
 * @return Returns a new [TransactionState] instance cast to the specified type.
 * @throws IllegalArgumentException if the current [TransactionState] cannot be cast to the specified type.
 */
fun <T : ContractState> TransactionState<*>.cast(type: Class<T>): TransactionState<T> {
    return SimTransactionState(contractState.cast(type), notaryName, notaryKey, encumbranceGroup)
}

/**
 * Casts the current [StateAndRef] to the specified type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast to.
 * @return Returns a new [StateAndRef] instance cast to the specified type.
 * @throws IllegalArgumentException if the current [StateAndRef] cannot be cast to the specified type.
 */
fun <T : ContractState> StateAndRef<*>.cast(type: Class<T>): StateAndRef<T> {
    return SimStateAndRef(state.cast(type), ref)
}
