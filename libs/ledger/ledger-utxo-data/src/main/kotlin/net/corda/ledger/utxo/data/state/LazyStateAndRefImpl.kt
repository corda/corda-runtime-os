package net.corda.ledger.utxo.data.state

import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import java.lang.Exception

/**
 * Lazy [StateRef]. It stores initially the serialized form of the represented state and ref.
 * Its purposes are to avoid
 *  - unnecessary deserializations to perform better
 *  - deserializations whose required CPKs are not necessarily available.
 *
 * @constructor Creates a new instance of the [LazyStateAndRefImpl] data class.
 * @property serializedStateAndRef A [UtxoTransactionOutputDto] with the serialized information
 */
@CordaSerializable
data class LazyStateAndRefImpl<out T : ContractState>(
    val serializedStateAndRef: UtxoTransactionOutputDto,
    private val serializationService: SerializationService
) : StateAndRefInternal<@UnsafeVariance T> {

    private val representedState: TransactionState<@UnsafeVariance T> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        serializedStateAndRef.deserializeTransactionState<@UnsafeVariance T>(serializationService)
    }

    private val representedStateRef:StateRef by lazy(LazyThreadSafetyMode.PUBLICATION) {
        serializedStateAndRef.getStateRef()
    }

    override fun toUtxoTransactionOutputDto(
        serializationService: SerializationService,
        currentSandboxGroup: SandboxGroup
    ): UtxoTransactionOutputDto {
        return serializedStateAndRef
    }

    override fun getState(): TransactionState<@UnsafeVariance T> {
        return representedState
    }

    override fun getRef(): StateRef {
        return representedStateRef
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
        return serializedStateAndRef.hashCode()
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : ContractState> UtxoTransactionOutputDto.deserializeTransactionState(
    serializationService: SerializationService
): TransactionState<T> {
    val info = try{
        serializationService.deserialize<UtxoOutputInfoComponent>(info)
    } catch (e: Exception){
        throw CordaRuntimeException("Deserialization of $info into UtxoOutputInfoComponent failed.", e)
    }
    val contractState = try{
        serializationService.deserialize<ContractState>(data)
    } catch (e: Exception){
        throw CordaRuntimeException("Deserialization of $data into ContractState failed.", e)
    }
    return TransactionStateImpl(
        contractState as T,
        info.notaryName,
        info.notaryKey,
        info.getEncumbranceGroup()
    )
}

private fun UtxoTransactionOutputDto.getStateRef(): StateRef =
    StateRef(parseSecureHash(transactionId), leafIndex)
