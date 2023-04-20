package net.corda.ledger.utxo.data.state

import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import java.util.Objects

/**
 * Represents a composition of a [TransactionState] and a [StateRef].
 *
 * @constructor Creates a new instance of the [StateAndRef] data class.
 * @property state The [TransactionState] component of the current [StateAndRef].
 * @property ref The [StateRef] component of the current [StateAndRef].
 */
@CordaSerializable
data class StateAndRefImpl<out T : ContractState>(
    private val state: TransactionState<T>,
    private val ref: StateRef
) : StateAndRefInternal<@UnsafeVariance T> {

    override fun getState(): TransactionState<@UnsafeVariance T> {
        return state
    }

    override fun getRef(): StateRef {
        return ref
    }

    override fun toUtxoTransactionOutputDto(
        serializationService: SerializationService,
        currentSandboxGroup: SandboxGroup
    ): UtxoTransactionOutputDto {
        val info = UtxoOutputInfoComponent(
            state.encumbranceGroup?.tag,
            state.encumbranceGroup?.size,
            state.notaryName,
            state.notaryKey,
            currentSandboxGroup.getEvolvableTag(state.contractStateType),
            currentSandboxGroup.getEvolvableTag(state.contractType)
        )

        return UtxoTransactionOutputDto(
            ref.transactionId.toString(),
            ref.index,
            serializationService.serialize(info).bytes,
            serializationService.serialize(state.contractState).bytes
        )
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param other The object to compare with the current object.
     * @return Returns true if the specified object is equal to the current object; otherwise, false.
     */
    override fun equals(other: Any?): Boolean {
        return this === other
                || other != null
                && other is StateAndRef<*>
                && other.ref == ref
                && other.state == state
    }

    /**
     * Serves as the default hash function.
     *
     * @return Returns a hash code for the current object.
     */
    override fun hashCode(): Int {
        return Objects.hash(ref, state)
    }
}
