package net.corda.ledger.utxo.data.state

import net.corda.crypto.core.bytes
import net.corda.ledger.common.data.transaction.getRootMerkleTreeDigestProvider
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import java.util.Objects

/**
 * Lazy [StateRef]. It stores initially the serialized representation of the represented state and ref.
 * Its purposes are to avoid
 *  - unnecessary deserialization to perform better
 *  - deserialization whose required CPKs are not necessarily available.
 *
 * @constructor Creates a new instance of the [LazyStateAndRefImpl] data class.
 * @property serializedStateAndRef A [UtxoTransactionOutputDto] with the serialized information
 */
@CordaSerializable
data class LazyStateAndRefImpl<out T : ContractState>(
    val serializedStateAndRef: UtxoTransactionOutputDto,
    private val serializationService: SerializationService
) : StateAndRef<@UnsafeVariance T> {
    private val stateAndRef: StateAndRef<T> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        serializedStateAndRef.toStateAndRef<T>(serializationService)
    }

    override fun getState(): TransactionState<@UnsafeVariance T> {
        return stateAndRef.state
    }

    override fun getRef(): StateRef {
        return stateAndRef.ref
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
                && other is LazyStateAndRefImpl<*>
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
