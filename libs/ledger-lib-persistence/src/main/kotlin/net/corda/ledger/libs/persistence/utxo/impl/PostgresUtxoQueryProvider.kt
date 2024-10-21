package net.corda.ledger.libs.persistence.utxo.impl

import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup

class PostgresUtxoQueryProvider : AbstractUtxoQueryProvider() {
    override fun wrapInList(placeHolder: String): String {
        return placeHolder
    }

    override val persistTransaction: String
        get() = """
            INSERT INTO utxo_transaction(id, privacy_salt, account_id, created, status, updated, metadata_hash, is_filtered, repair_attempt_count)
                VALUES (:id, :privacySalt, :accountId, :createdAt, :status, :updatedAt, :metadataHash, FALSE, 0)
            ON CONFLICT(id) DO
            UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated, is_filtered = FALSE
            WHERE utxo_transaction.status in ('$UNVERIFIED', '$DRAFT') AND utxo_transaction.is_filtered = FALSE
            """
            .trimIndent()

    override val persistUnverifiedTransaction: String
        get() = """
            INSERT INTO utxo_transaction(id, privacy_salt, account_id, created, status, updated, metadata_hash, is_filtered, repair_attempt_count)
                VALUES (:id, :privacySalt, :accountId, :createdAt, '$UNVERIFIED', :updatedAt, :metadataHash, FALSE, 0)
            ON CONFLICT(id) DO
            UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
            WHERE utxo_transaction.status in ('$UNVERIFIED', '$DRAFT')
                OR (utxo_transaction.status = '$VERIFIED' AND utxo_transaction.is_filtered = TRUE)
            """
            .trimIndent()

    override val persistFilteredTransaction: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction(id, privacy_salt, account_id, created, status, updated, metadata_hash, is_filtered, repair_attempt_count)
            VALUES ${List(batchSize) { "(?, ?, ?, ?, '$VERIFIED', ?, ?, TRUE, 0)" }.joinToString(",")}
            ON CONFLICT(id) DO
            UPDATE SET is_filtered = TRUE
            WHERE utxo_transaction.status in ('$UNVERIFIED', '$DRAFT') AND utxo_transaction.is_filtered = FALSE
            """.trimIndent()
        }

    override val persistTransactionMetadata: String
        get() = """
            INSERT INTO utxo_transaction_metadata(hash, canonical_data, group_parameters_hash, cpi_file_checksum)
                VALUES (:hash, :canonicalData, :groupParametersHash, :cpiFileChecksum)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionSources: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_sources(transaction_id, group_idx, leaf_idx, source_state_transaction_id, source_state_idx)
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?)" }.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistTransactionComponents: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_component(transaction_id, group_idx, leaf_idx, data, hash)
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?)" }.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistVisibleTransactionOutputs: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_visible_transaction_output(
                transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                token_symbol, token_tag, token_owner_hash, token_amount, token_priority, created, consumed, custom_representation
            )
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? as JSONB))"}.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistTransactionSignatures: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_signature(transaction_id, pub_key_hash, signature, created)
            VALUES ${List(batchSize) { "(?, ?, ?, ?)" }.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistSignedGroupParameters: String
        get() = """
            INSERT INTO utxo_group_parameters(
                hash, parameters, signature_public_key, signature_content, signature_spec, created)
            VALUES (
                :hash, :parameters, :signature_public_key, :signature_content, :signature_spec, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistMerkleProofs: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_merkle_proof(merkle_proof_id, transaction_id, group_idx, tree_size, leaf_indexes, hashes)
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?, ?)" }.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistMerkleProofLeaves: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_merkle_proof_leaves(merkle_proof_id, leaf_index)
            VALUES ${List(batchSize) { "(?, ?)" }.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val stateRefsExist: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            SELECT transaction_id, leaf_idx
            FROM utxo_transaction_component
            WHERE (transaction_id, group_idx, leaf_idx) in (VALUES
                ${List(batchSize) { "(?, ${UtxoComponentGroup.OUTPUTS.ordinal}, ?)" }.joinToString(",")}
            )
            """.trimIndent()
        }
}
