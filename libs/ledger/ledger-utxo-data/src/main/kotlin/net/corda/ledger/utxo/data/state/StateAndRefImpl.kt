package net.corda.ledger.utxo.data.state

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
) : StateAndRef<@UnsafeVariance T> {

    override fun getState(): TransactionState<@UnsafeVariance T> {
        return state
    }

    override fun getRef(): StateRef {
        return ref
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
                && other is StateAndRefImpl<*>
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
