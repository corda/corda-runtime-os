package net.corda.messagebus.db.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

enum class TransactionState {
    PENDING, COMMITTED, ABORTED
}

/**
 * This entity represents the state of a transaction.  Each record will map to a
 * transaction available in the `transaction_record` table.  Atomic transactions
 * (using a non-transactional producer) will map to a static [ATOMIC_TRANSACTION].
 */
@Entity(name = "transaction_record")
@Table(name = "transaction_record")
class TransactionRecordEntry(
    @Id
    @Column(name = "transaction_id")
    val transactionId: String,

    @Column
    var state: TransactionState = TransactionState.PENDING,
) {
    // Overrides needed for comparison in tests
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionRecordEntry) return false

        if (transactionId != other.transactionId) return false
        if (state != other.state) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + state.hashCode()
        return result
    }

    override fun toString(): String {
        return "TransactionRecordEntry(transactionId='$transactionId', state=$state)"
    }
}
