package net.corda.messagebus.db.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

enum class TransactionState {
    PENDING, COMMITTED, ABORTED
}

@Entity(name = "transaction_record")
@Table(name = "transaction_record")
class TransactionRecordEntry(
    @Id
    @Column(name = "transaction_id")
    val transactionId: String,

    @Column
    var state: TransactionState = TransactionState.PENDING,
)
