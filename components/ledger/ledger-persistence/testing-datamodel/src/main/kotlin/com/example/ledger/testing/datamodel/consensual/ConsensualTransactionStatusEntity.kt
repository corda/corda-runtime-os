package com.example.ledger.testing.datamodel.consensual

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@CordaSerializable
@Entity
@Table(name = "consensual_transaction_status")
@IdClass(ConsensualTransactionStatusEntityId::class)
data class ConsensualTransactionStatusEntity(
    @get:Id
    @get:ManyToOne
    @get:JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    var transaction: ConsensualTransactionEntity,

    @get:Id
    @get:Column(name = "status", nullable = false)
    var status: String,

    @get:Column(name = "updated", nullable = false)
    var updated: Instant
)

@Embeddable
data class ConsensualTransactionStatusEntityId(
    var transaction: ConsensualTransactionEntity,
    var status: String
) : Serializable
