package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.utxo.data.state.LazyStateAndRefImpl
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import java.util.Objects

data class UtxoVisibleTransactionOutputDto(
    val transactionId: String,
    val leafIndex: Int,
    val info: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoVisibleTransactionOutputDto

        if (transactionId != other.transactionId) return false
        if (leafIndex != other.leafIndex) return false
        if (!info.contentEquals(other.info)) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(transactionId, leafIndex, info, data)

    fun <T : ContractState> toStateAndRef(serializationService: SerializationService) =
        LazyStateAndRefImpl<T>(
            this,
            null,
            serializationService
        )
}
