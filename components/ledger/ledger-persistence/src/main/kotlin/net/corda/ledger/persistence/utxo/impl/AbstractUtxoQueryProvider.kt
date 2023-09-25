package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup

abstract class AbstractUtxoQueryProvider : UtxoQueryProvider {
    companion object {
        @JvmField
        val UNVERIFIED = TransactionStatus.UNVERIFIED.value
    }

    override val findTransactionPrivacySalt: String
        get() = """
            SELECT privacy_salt
            FROM {h-schema}utxo_transaction
            WHERE id = :transactionId"""
            .trimIndent()

    override val findTransactionComponentLeafs: String
        get() = """
            SELECT group_idx, leaf_idx, data
            FROM {h-schema}utxo_transaction_component
            WHERE transaction_id = :transactionId
            ORDER BY group_idx, leaf_idx"""
            .trimIndent()

    override val findUnconsumedVisibleStatesByType: String
        get() = """
            SELECT tc_output.transaction_id, 
            tc_output.leaf_idx, 
            tc_output_info.data as output_info_data,
            tc_output.data AS output_data 
            FROM {h-schema}utxo_visible_transaction_state AS rts
            JOIN {h-schema}utxo_transaction_component AS tc_output_info
                ON tc_output_info.transaction_id = rts.transaction_id
                AND tc_output_info.leaf_idx = rts.leaf_idx
                AND tc_output_info.group_idx = ${UtxoComponentGroup.OUTPUTS_INFO.ordinal}
            JOIN {h-schema}utxo_transaction_component AS tc_output
                ON tc_output.transaction_id = tc_output_info.transaction_id
                AND tc_output.leaf_idx = tc_output_info.leaf_idx
                AND tc_output.group_idx = ${UtxoComponentGroup.OUTPUTS.ordinal}
            JOIN {h-schema}utxo_transaction_status AS ts
                ON ts.transaction_id = tc_output.transaction_id
            AND rts.consumed IS NULL
            AND ts.status = :verified
            ORDER BY tc_output.created, tc_output.transaction_id, tc_output.leaf_idx"""
            .trimIndent()

    override val findUnconsumedVisibleStatesByExactType: String
        get() = """
            SELECT tc_output.transaction_id, 
            tc_output.leaf_idx, 
            tc_output_info.data as output_info_data,
            tc_output.data AS output_data 
            FROM {h-schema}utxo_visible_transaction_state AS rts
            JOIN {h-schema}utxo_transaction_component AS tc_output_info
                ON tc_output_info.transaction_id = rts.transaction_id
                AND tc_output_info.leaf_idx = rts.leaf_idx
                AND tc_output_info.group_idx = ${UtxoComponentGroup.OUTPUTS_INFO.ordinal}
            JOIN {h-schema}utxo_transaction_component AS tc_output
                ON tc_output.transaction_id = tc_output_info.transaction_id
                AND tc_output.leaf_idx = tc_output_info.leaf_idx
                AND tc_output.group_idx = ${UtxoComponentGroup.OUTPUTS.ordinal}
            JOIN {h-schema}utxo_transaction_output AS tx_o
                ON tx_o.transaction_id = tc_output.transaction_id
                AND tx_o.leaf_idx = tc_output.leaf_idx
            JOIN {h-schema}utxo_transaction_status AS ts
                ON ts.transaction_id = tx_o.transaction_id
            WHERE tx_o.type = :type    
                AND rts.consumed IS NULL
                AND ts.status = :verified
            ORDER BY tc_output.created, tc_output.transaction_id, tc_output.leaf_idx"""
            .trimIndent()

    override val findTransactionSignatures: String
        get() = """
            SELECT signature
            FROM {h-schema}utxo_transaction_signature
            WHERE transaction_id = :transactionId
            ORDER BY signature_idx"""
            .trimIndent()

    override val findTransactionStatus: String
        get() = """
            SELECT status
            FROM {h-schema}utxo_transaction_status
            WHERE transaction_id = :transactionId"""
            .trimIndent()

    override val markTransactionVisibleStatesConsumed: String
        get() = """
            UPDATE {h-schema}utxo_visible_transaction_state
            SET consumed = :consumed
            WHERE transaction_id in (:transactionIds)
            AND (transaction_id || ':' || leaf_idx) IN (:stateRefs)"""
            .trimIndent()

    override val findSignedGroupParameters: String
        get() = """
            SELECT
                parameters,
                signature_public_key,
                signature_content,
                signature_spec
            FROM {h-schema}utxo_group_parameters
            WHERE hash = :hash"""
            .trimIndent()

    override val resolveStateRefs: String
        get() = """
            SELECT tc_output.transaction_id, 
				tc_output.leaf_idx, 
				tc_output_info.data as output_info_data,
                tc_output.data AS output_data 
            FROM {h-schema}utxo_transaction_component AS tc_output_info  
            JOIN {h-schema}utxo_transaction_component AS tc_output
                ON tc_output.transaction_id = tc_output_info.transaction_id
                AND tc_output.leaf_idx = tc_output_info.leaf_idx
                AND tc_output.group_idx = ${UtxoComponentGroup.OUTPUTS.ordinal}
            JOIN {h-schema}utxo_transaction_status AS ts
                ON ts.transaction_id = tc_output.transaction_id
            AND tc_output.transaction_id in (:transactionIds)
            AND (tc_output.transaction_id||':'|| tc_output.leaf_idx) in (:stateRefs)
            AND ts.status = :verified
            AND tc_output_info.group_idx = ${UtxoComponentGroup.OUTPUTS_INFO.ordinal}
            ORDER BY tc_output.created, tc_output.transaction_id, tc_output.leaf_idx"""
            .trimIndent()
}
