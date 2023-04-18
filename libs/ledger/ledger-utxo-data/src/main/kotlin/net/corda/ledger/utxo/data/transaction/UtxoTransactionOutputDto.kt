package net.corda.ledger.utxo.data.transaction

import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import java.util.Objects

data class UtxoTransactionOutputDto(
    val transactionId: String,
    val leafIndex: Int,
    val info: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionOutputDto

        if (transactionId != other.transactionId) return false
        if (leafIndex != other.leafIndex) return false
        if (!info.contentEquals(other.info)) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(transactionId, leafIndex, info, data)

    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> toStateAndRef(serializationService: SerializationService): StateAndRef<T> {
        val info = serializationService.deserialize<UtxoOutputInfoComponent>(info)
        val contractState = serializationService.deserialize<ContractState>(data)
        return StateAndRefImpl(
            state = TransactionStateImpl(
                contractState as T,
                info.notaryName,
                info.notaryKey,
                info.getEncumbranceGroup()
            ),
            ref = StateRef(parseSecureHash(transactionId), leafIndex)
        )
    }
}