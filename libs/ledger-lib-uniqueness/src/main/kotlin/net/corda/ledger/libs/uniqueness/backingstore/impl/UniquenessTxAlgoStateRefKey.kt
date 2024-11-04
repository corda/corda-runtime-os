package net.corda.ledger.libs.uniqueness.backingstore.impl

import java.io.Serializable

class UniquenessTxAlgoStateRefKey(
    var issueTxIdAlgo: String = "",
    var issueTxId: ByteArray = ByteArray(0),
    var issueTxOutputIndex: Int = 0
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UniquenessTxAlgoStateRefKey

        if (issueTxIdAlgo != other.issueTxIdAlgo) return false
        if (!issueTxId.contentEquals(other.issueTxId)) return false
        if (issueTxOutputIndex != other.issueTxOutputIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = issueTxIdAlgo.hashCode()
        result = 31 * result + issueTxId.contentHashCode()
        result = 31 * result + issueTxOutputIndex.hashCode()
        return result
    }

    companion object {
        private const val serialVersionUID = -14548L
    }
}
