package net.corda.ledger.persistence.common

import java.util.Objects

data class TransactionOutputDto(
    val transactionId: String,
    val leafIndex: Int,
    val info: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionOutputDto

        if (transactionId != other.transactionId) return false
        if (leafIndex != other.leafIndex) return false
        if (!info.contentEquals(other.info)) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(transactionId, leafIndex, info, data)
}