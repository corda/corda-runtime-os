package net.corda.ledger.persistence.common

data class ComponentLeafDto(
    val transactionId: String,
    val groupIndex: Int,
    val leafIndex: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComponentLeafDto

        if (transactionId != other.transactionId) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex
        result = 31 * result + data.contentHashCode()
        return result
    }
}