package com.example.ledger.testing.datamodel.consensual

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
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
@Entity
@Table(name = "consensual_transaction_status")
@IdClass(ConsensualTransactionStatusEntityId::class)
data class ConsensualTransactionStatusEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    val transaction: ConsensualTransactionEntity,

    @Id
    @Column(name = "status", nullable = false)
    val status: String,

    @Column(name = "updated", nullable = false)
    val updated: Instant
)

@Embeddable
data class ConsensualTransactionStatusEntityId(
    val transaction: ConsensualTransactionEntity,
    val status: String
) : Serializable