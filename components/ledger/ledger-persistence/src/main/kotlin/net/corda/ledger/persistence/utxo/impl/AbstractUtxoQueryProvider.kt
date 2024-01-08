package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup

abstract class AbstractUtxoQueryProvider : UtxoQueryProvider {
    companion object {
        @JvmField
        val UNVERIFIED = TransactionStatus.UNVERIFIED.value

        @JvmField
        val DRAFT = TransactionStatus.DRAFT.value
    }

    override val findTransactionIdsAndStatuses: String
        get() = """
            SELECT id, status 
            FROM {h-schema}utxo_transaction 
            WHERE id IN (:transactionIds)"""
            .trimIndent()

    override val findTransactionPrivacySaltAndMetadata: String
        get() = """
            SELECT privacy_salt,
            utm.canonical_data
            FROM {h-schema}utxo_transaction AS ut
            JOIN {h-schema}utxo_transaction_metadata AS utm
                ON ut.metadata_hash = utm.hash
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
            FROM {h-schema}utxo_visible_transaction_output AS vto
            JOIN {h-schema}utxo_transaction_component AS tc_output_info
                ON tc_output_info.transaction_id = vto.transaction_id
                AND tc_output_info.leaf_idx = vto.leaf_idx
                AND tc_output_info.group_idx = ${UtxoComponentGroup.OUTPUTS_INFO.ordinal}
            JOIN {h-schema}utxo_transaction_component AS tc_output
                ON tc_output.transaction_id = tc_output_info.transaction_id
                AND tc_output.leaf_idx = tc_output_info.leaf_idx
                AND tc_output.group_idx = ${UtxoComponentGroup.OUTPUTS.ordinal}
            JOIN {h-schema}utxo_transaction AS tx
                ON tx.id = tc_output.transaction_id
            AND vto.consumed IS NULL
            AND tx.status = :verified
            ORDER BY tx.created, tc_output.transaction_id, tc_output.leaf_idx"""
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
            FROM {h-schema}utxo_transaction
            WHERE id = :transactionId"""
            .trimIndent()

    override val markTransactionVisibleStatesConsumed: String
        get() = """
            UPDATE {h-schema}utxo_visible_transaction_output
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

//    override val findMerkleProofs: String
//        get() = """
//            SELECT
//                transaction_id,
//                group_idx,
//                tree_size,
//                array_to_string(leaves, ',') as leaves_string,
//                array_to_string(hashes, ',') as hashes_string
//            FROM {h-schema}utxo_transaction_merkle_proof
//            WHERE transaction_id = :transactionId
//            AND group_idx = :groupId"""
//            .trimIndent()

    override val findMerkleProofs: String
        get() = """SELECT 
                utc.transaction_id,
                utc.group_idx,
                ump.tree_size,
                array_to_string(ump.leaves, ',') AS leaves_string,
                utc.leaf_idx, 
                array_to_string(ump.hashes, ',') AS hashes_string,
                utc."data"
            FROM utxo_transaction_merkle_proof ump 
            JOIN utxo_transaction_component utc 
                ON utc.transaction_id = ump.transaction_id 
                AND utc.group_idx = ump.group_idx 
                AND utc.leaf_idx = any(ump.leaves)
                AND ump.group_idx = :groupId
                AND ump.transaction_id = :transactionId"""
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
            JOIN {h-schema}utxo_transaction AS tx
                ON tx.id = tc_output.transaction_id
            AND tc_output.transaction_id in (:transactionIds)
            AND (tc_output.transaction_id||':'|| tc_output.leaf_idx) in (:stateRefs)
            AND tx.status = :verified
            AND tc_output_info.group_idx = ${UtxoComponentGroup.OUTPUTS_INFO.ordinal}
            ORDER BY tx.created, tc_output.transaction_id, tc_output.leaf_idx"""
            .trimIndent()

    override val updateTransactionStatus: String
        get() = """
            UPDATE {h-schema}utxo_transaction SET status = :newStatus, updated = :updatedAt
            WHERE id = :transactionId 
            AND (status = :newStatus OR status = '$UNVERIFIED')"""
            .trimIndent()
}
