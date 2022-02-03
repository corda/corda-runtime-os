package net.corda.messagebus.db.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity(name = "transaction_record")
@Table(name = "transaction_record")
class TransactionRecordEntry(
    @Id
    @Column(name = "transaction_id")
    val transactionId: String,

    @Column
    var visible: Boolean
)
