package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import java.time.Instant

fun randomBytes(): ByteArray {
    return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
}

data class StateDetails(val sRef: UniquenessCheckStateRef, val consumingId: SecureHash?):
    UniquenessCheckStateDetails {
    override fun getStateRef() = sRef
    override fun getConsumingTxId(): SecureHash? = consumingId
}

data class StateRef(val hash: SecureHash, val index: Int):
    UniquenessCheckStateRef {
    override fun getTxHash(): SecureHash = hash
    override fun getStateIndex(): Int = index
}

data class TransactionDetails(
    val txIdAlgo: String,
    val txId: ByteArray,
    val originatorX500Name: String,
    val commitTimestamp: Instant,
    val result: Char
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionDetails

        if (txIdAlgo != other.txIdAlgo) return false
        if (!txId.contentEquals(other.txId)) return false
        if (originatorX500Name != other.originatorX500Name) return false
        if (commitTimestamp != other.commitTimestamp) return false
        if (result != other.result) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = txIdAlgo.hashCode()
        result1 = 31 * result1 + txId.contentHashCode()
        result1 = 31 * result1 + originatorX500Name.hashCode()
        result1 = 31 * result1 + commitTimestamp.hashCode()
        result1 = 31 * result1 + result.hashCode()
        return result1
    }
}