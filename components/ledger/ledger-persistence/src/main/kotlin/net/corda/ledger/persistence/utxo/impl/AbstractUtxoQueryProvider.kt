package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup

abstract class AbstractUtxoQueryProvider : UtxoQueryProvider {
    companion object {
        @JvmField
        val DRAFT = TransactionStatus.DRAFT.value

        @JvmField
        val UNVERIFIED = TransactionStatus.UNVERIFIED.value

        @JvmField
        val VERIFIED = TransactionStatus.VERIFIED.value
    }

    override val findTransactionIdsAndStatuses: String
        get() = """
            SELECT id, status 
            FROM {h-schema}utxo_transaction 
            WHERE id IN (:transactionIds)"""
            .trimIndent()

    override val findTransactionsPrivacySaltAndMetadata: String
        get() = """
            SELECT 
                id,
                privacy_salt,
                utm.canonical_data
            FROM {h-schema}utxo_transaction AS ut
            JOIN {h-schema}utxo_transaction_metadata AS utm
                ON ut.metadata_hash = utm.hash
            WHERE id IN (:transactionIds)"""
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
            SELECT status, is_filtered
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

    /**
     * This query will join the Merkle proof table and the component group table together to find the leaf data the
     * Merkle proof has revealed.
     *
     * Each Merkle proof has an ID created by concatenating the following properties:
     * - Transaction ID
     * - Group Index
     * - Revealed leaf indices joined to a string
     *
     * The indices that the Merkle proof reveals are stored in a join-table keyed on the Merkle proof ID.
     * That's the reason we need a sub-query to fetch the relevant indices from the other table.
     *
     * This query will return a result set with the following column structure:
     *
     * | merkle_proof_id | transaction_id | group_idx | tree_size | hashes | privacy_salt | leaf_index  | data |
     *
     * A row will be returned for each revealed leaf containing the data from the component table.
     * If the data couldn't be found in the component table then the data field will be `null` beause of the LEFT JOIN.
     *
     * For example if we have the following Merkle tree:
     *
     *              COMPONENT GROUP ROOT
     *                     /    \
     *                    /      \
     *                   /        \
     *                  /          \
     *                 /            \
     *               H01            H23
     *              / \             / \
     *             /   \           /   \
     *            /     \         /     \
     *           H0     H1       H2     H3
     *           |      |        |      |
     *          L0     L1       L2     L3
     *
     * If we persist a Merkle proof that contains L1 and L3 data then retrieve it from the store, then we'll
     * get the following result set:
     *
     * | merkle_proof_id      | transaction_id | group_idx | tree_size | hashes       | privacy_salt  | leaf_idx | data |
     * |----------------------|----------------|-----------|-----------|--------------|---------------|----------|------|
     * | SHA-256D:11111;8;1,3 | SHA-256:11111  | 8         | 4         | H0,H2        | bytes         | 1        |bytes |
     * | SHA-256D:11111;8;1,3 | SHA-256:11111  | 8         | 4         | H0,H2        | bytes         | 3        |bytes |
     */
    override val findMerkleProofs: String
        get() = """
            SELECT
                utmp.merkle_proof_id,
                utmp.transaction_id,
                utmp.group_idx,
                utmp.tree_size,
                utmp.hashes,
                utt.privacy_salt,
                utc.leaf_idx,
                utc.data
            FROM utxo_transaction_merkle_proof utmp
            JOIN utxo_transaction utt
                ON utt.id = utmp.transaction_id
            LEFT JOIN utxo_transaction_component utc
                ON utc.transaction_id = utmp.transaction_id
                AND utc.group_idx = utmp.group_idx
                AND utc.leaf_idx IN (
                	SELECT utmpl.leaf_index
                	FROM utxo_transaction_merkle_proof_leaves utmpl
                	WHERE utmpl.merkle_proof_id = utmp.merkle_proof_id
            	)
            WHERE utmp.transaction_id IN (:transactionIds)"""
            .trimIndent()
}
