package net.corda.ledger.persistence.utxo.impl

import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ UtxoQueryProvider::class ])
class PostgresUtxoQueryProvider @Activate constructor(
    @Reference(target = POSTGRES_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
) : AbstractUtxoQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).debug { "Activated for ${databaseTypeProvider.databaseType}" }
    }

    override val persistTransaction: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction(id, privacy_salt, account_id, created, status, updated, metadata_hash, is_filtered)
                VALUES (:id, :privacySalt, :accountId, :createdAt, :status, :updatedAt, :metadataHash, :isFiltered)
            ON CONFLICT(id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
            WHERE utxo_transaction.status in ('$UNVERIFIED', '$DRAFT')"""
            .trimIndent()

    override val persistTransactionMetadata: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_metadata(hash, canonical_data, group_parameters_hash, cpi_file_checksum)
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
                token_symbol, token_tag, token_owner_hash, token_amount, created, consumed, custom_representation
            )
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? as JSONB))"}.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistTransactionSignatures: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_signature(transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?)" }.joinToString(",")}
            ON CONFLICT DO NOTHING
            """.trimIndent()
        }

    override val persistSignedGroupParameters: String
        get() = """
            INSERT INTO {h-schema}utxo_group_parameters(
                hash, parameters, signature_public_key, signature_content, signature_spec, created)
            VALUES (
                :hash, :parameters, :signature_public_key, :signature_content, :signature_spec, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistMerkleProofs: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            INSERT INTO utxo_transaction_merkle_proof(merkle_proof_id, transaction_id, group_idx, tree_size, hashes)
            VALUES ${List(batchSize) { "(?, ?, ?, ?, ?)" }.joinToString(",")}
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
}
