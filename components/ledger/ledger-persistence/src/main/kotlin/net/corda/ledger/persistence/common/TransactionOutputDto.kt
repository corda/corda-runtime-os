package net.corda.ledger.persistence.common

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

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + leafIndex
        result = 31 * result + info.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}