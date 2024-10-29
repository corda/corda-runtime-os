package net.corda.ledger.libs.uniqueness.backingstore.impl

class DefaultSqlQueryProvider : SqlQueryProvider {
    override fun findStatesByKeyQuery(): String {
        return """
            SELECT issue_tx_id_algo, issue_tx_id, issue_tx_output_idx, consuming_tx_id_algo, consuming_tx_id
            FROM uniqueness_state_details
            WHERE (issue_tx_id_algo, issue_tx_id, issue_tx_output_idx) = 
            ANY(
                SELECT UNNEST(? :: text[]),
                SELECT UNNEST(? :: VARBINARY[]),
                SELECT UNNEST(? :: int[])
            )
        """.trimIndent()
    }

    override fun findTransactionDetailByKeyQuery(): String {
        return """
            SELECT tx_id_algo, tx_id, commit_timestamp, result
            FROM uniqueness_tx_details
            WHERE (tx_id_algo, tx_id) =
            ANY(
                SELECT UNNEST(? :: text[]),
                SELECT UNNEST(? :: VARBINARY[])
            )
        """.trimIndent()
    }

    override fun findRejectedTransactionQuery(): String {
        return """
            SELECT error_details FROM UniquenessRejectedTransactionEntity
            WHERE txIdAlgo = ? AND txId = ?
        """.trimIndent()
    }

    override fun insertUnconsumedStatesQuery(): String {
        return """
            INSERT INTO uniqueness_state_details(
                issue_tx_id_algo,
                issue_tx_id,
                issue_tx_output_idx,
                consuming_tx_id_algo,
                consuming_tx_id,
            )
            VALUES (?,?,?,NULL,NULL)
        """.trimIndent()
    }

    override fun consumeStatesQuery(): String {
        return """
            UPDATE UniquenessStateDetailEntity SET 
                consumingTxIdAlgo = ?, 
                consumingTxId = ? 
            WHERE 
                issueTxIdAlgo = ? AND 
                issueTxId = ? AND 
                issueTxOutputIndex = ? AND 
                consumingTxId IS NULL
        """.trimIndent()
    }

    override fun insertTransactionDetailsQuery(): String {
        return """
            INSERT INTO uniqueness_tx_details(
                tx_id_algo,
                tx_id,
                originator_x500_name,
                expiry_datetime,
                commit_timestamp,
                result
            ) VALUES (?,?,?,?,?,?)
        """.trimIndent()
    }

    override fun insertRejectedTransactionQuery(): String {
        return """
            INSERT INTO uniqueness_rejected_txs(
                tx_id_algo,
                tx_id,
                error_details
            ) VALUES (?,?,?)
        """.trimIndent()
    }

}
