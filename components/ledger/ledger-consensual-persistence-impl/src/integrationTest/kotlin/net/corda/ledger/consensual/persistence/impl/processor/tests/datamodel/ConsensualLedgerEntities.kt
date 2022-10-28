package net.corda.ledger.consensual.persistence.impl.processor.tests.datamodel

object ConsensualLedgerEntities {
    val classes = setOf(
        ConsensualCpkEntity::class.java,
        ConsensualTransactionComponentEntity::class.java,
        ConsensualTransactionEntity::class.java,
        ConsensualTransactionSignatureEntity::class.java,
        ConsensualTransactionStatusEntity::class.java
    )
}
