package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.common.data.transaction.TransactionStatus

abstract class AbstractConsensualQueryProvider : ConsensualQueryProvider {
    companion object {
        @JvmField
        val UNVERIFIED = TransactionStatus.UNVERIFIED.value
    }

    override val findTransaction: String
        get() = """
            SELECT tx.id, tx.privacy_salt, tx.account_id, tx.created, txc.group_idx, txc.leaf_idx, txc.data
            FROM {h-schema}consensual_transaction AS tx
            JOIN {h-schema}consensual_transaction_component AS txc ON tx.id = txc.transaction_id
            WHERE tx.id = :id
            ORDER BY txc.group_idx, txc.leaf_idx"""
            .trimIndent()

    override val findTransactionCpkChecksums: String
        get() = """
            SELECT file_checksum
            FROM {h-schema}consensual_transaction_cpk
            WHERE file_checksum in (:fileChecksums)"""
            .trimIndent()

    override val findTransactionSignatures: String
        get() = """
            SELECT signature
            FROM {h-schema}consensual_transaction_signature
            WHERE transaction_id = :transactionId
            ORDER BY signature_idx"""
            .trimIndent()
}
