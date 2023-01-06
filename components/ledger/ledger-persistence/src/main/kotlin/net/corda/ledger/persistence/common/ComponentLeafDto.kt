package net.corda.ledger.persistence.common

import java.util.Objects

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

    override fun hashCode(): Int = Objects.hash(transactionId, groupIndex, leafIndex, data)
}