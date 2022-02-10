package net.corda.messagebus.db.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

enum class TransactionState {
    PENDING, COMMITTED, ABORTED
}

val ATOMIC_TRANSACTION = TransactionRecordEntry("Atomic Transaction", TransactionState.COMMITTED)

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
)
