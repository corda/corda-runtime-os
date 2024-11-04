package net.corda.ledger.libs.uniqueness.backingstore.impl

import java.io.Serializable

class UniquenessTxAlgoIdKey(
    var txIdAlgo: String = "",
    var txId: ByteArray = ByteArray(0),
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessTxAlgoIdKey) return false

        if (txIdAlgo != other.txIdAlgo) return false
        if (!txId.contentEquals(other.txId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = txIdAlgo.hashCode()
        result = 31 * result + txId.contentHashCode()
        return result
    }

    companion object {
        private const val serialVersionUID = -30950023065170341L
    }
}
