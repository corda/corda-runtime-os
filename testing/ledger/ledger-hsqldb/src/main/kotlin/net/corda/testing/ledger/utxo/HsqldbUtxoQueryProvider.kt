package net.corda.testing.ledger.utxo

import net.corda.ledger.persistence.utxo.impl.AbstractUtxoQueryProvider
import net.corda.ledger.persistence.utxo.impl.UtxoQueryProvider
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.HSQLDB_TYPE_FILTER
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [UtxoQueryProvider::class])
class HsqldbUtxoQueryProvider @Activate constructor(
    @Reference(target = HSQLDB_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
) : AbstractUtxoQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).debug { "Activated for ${databaseTypeProvider.databaseType}" }
    }

    override val persistTransaction: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction AS ut
            USING (VALUES :id, CAST(:privacySalt AS VARBINARY(64)), :accountId, CAST(:createdAt AS TIMESTAMP), :status, CAST(:updatedAt AS TIMESTAMP), :metadataHash, :isFiltered)
                AS x(id, privacy_salt, account_id, created, status, updated, metadata_hash, is_filtered)
            ON x.id = ut.id
            WHEN NOT MATCHED THEN
                INSERT (id, privacy_salt, account_id, created, status, updated, metadata_hash, is_filtered)
                VALUES (x.id, x.privacy_salt, x.account_id, x.created, x.status, x.updated, x.metadata_hash, x.is_filtered)"""
            .trimIndent()

    override val persistTransactionMetadata: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_metadata AS m
            USING (VALUES :hash, CAST(:canonicalData AS VARBINARY(1048576)), :groupParametersHash, :cpiFileChecksum)
                AS x(hash, canonical_data, group_parameters_hash, cpi_file_checksum)
            ON x.hash = m.hash
            WHEN NOT MATCHED THEN
                INSERT (hash, canonical_data, group_parameters_hash, cpi_file_checksum)
                VALUES (x.hash, x.canonical_data, x.group_parameters_hash, x.cpi_file_checksum)"""
            .trimIndent()

    override val persistTransactionSources: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            MERGE INTO utxo_transaction_sources AS uts
            USING (VALUES${
                List(batchSize) {
                    "(?, CAST(? as INT), CAST(? AS INT), ?, CAST(? as INT))"
                }.joinToString(",")
            })
                AS x(transaction_id, group_idx, leaf_idx, source_state_transaction_id, source_state_idx)
            ON uts.transaction_id = x.transaction_id AND uts.group_idx = x.group_idx AND uts.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, source_state_transaction_id, source_state_idx)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.source_state_transaction_id, x.source_state_idx)
            """.trimIndent()
        }

    override val persistTransactionComponents: (batchSize: Int) -> String
        get() = { batchSize ->
            """
                MERGE INTO utxo_transaction_component AS utc
                USING (VALUES${
                List(batchSize) {
                    "(?, CAST(? AS INT), CAST(? AS INT), CAST(? AS VARBINARY(1048576)), ?)"
                }.joinToString(",")
            })
                    AS x(transaction_id, group_idx, leaf_idx, data, hash)
                ON x.transaction_id = utc.transaction_id AND x.group_idx = utc.group_idx AND x.leaf_idx = utc.leaf_idx
                WHEN NOT MATCHED THEN
                    INSERT (transaction_id, group_idx, leaf_idx, data, hash)
                    VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.data, x.hash)
                """.trimIndent()
        }

    override val persistVisibleTransactionOutputs: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            MERGE INTO utxo_visible_transaction_output AS uto
            USING (VALUES${
                List(batchSize) {
                    "(?, CAST(? AS INT), CAST(? AS INT), ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS TIMESTAMP), ?, ?)"
                }.joinToString(",")
            })
                AS x(transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                     token_symbol, token_tag, token_owner_hash, token_amount, created, consumed, custom_representation)
            ON uto.transaction_id = x.transaction_id AND uto.group_idx = x.group_idx AND uto.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                        token_symbol, token_tag, token_owner_hash, token_amount, created, consumed, custom_representation)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.type, x.token_type, x.token_issuer_hash, x.token_notary_x500_name,
                        x.token_symbol, x.token_tag, x.token_owner_hash, x.token_amount, x.created, x.consumed, x.custom_representation)
            """.trimIndent()
        }

    override val persistTransactionSignatures: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            MERGE INTO utxo_transaction_signature AS uts
            USING (VALUES${
                List(batchSize) {
                    "(?, CAST(? AS INT), CAST(? AS VARBINARY(1048576)), ?, CAST(? AS TIMESTAMP))"
                }.joinToString(",")
            })
                AS x(transaction_id, signature_idx, signature, pub_key_hash, created)
            ON uts.transaction_id = x.transaction_id AND uts.signature_idx = x.signature_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, signature_idx, signature, pub_key_hash, created)
                VALUES (x.transaction_id, x.signature_idx, x.signature, x.pub_key_hash, x.created)
            """.trimIndent()
        }

    override val persistSignedGroupParameters: String
        get() = """
            MERGE INTO {h-schema}utxo_group_parameters AS ugp
            USING (VALUES :hash, CAST(:parameters AS VARBINARY(1048576)),
                          CAST(:signature_public_key AS VARBINARY(1048576)), CAST(:signature_content AS VARBINARY(1048576)),
                          :signature_spec, CAST(:createdAt AS TIMESTAMP))
                AS x(hash, parameters, signature_public_key, signature_content, signature_spec, created)
            ON ugp.hash = x.hash
            WHEN NOT MATCHED THEN
                INSERT (hash, parameters, signature_public_key, signature_content, signature_spec, created)
                VALUES (x.hash, x.parameters, x.signature_public_key, x.signature_content, x.signature_spec, x.created)"""
            .trimIndent()

    override val persistMerkleProofs: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            MERGE INTO utxo_transaction_merkle_proof AS utmp
            USING (VALUES${
                List(batchSize) {
                    "(?, ?, ?, ?, ?)"
                }.joinToString(",")
            })
                AS x(merkle_proof_id, transaction_id, group_idx, tree_size, hashes)
            ON utmp.merkle_proof_id = x.merkle_proof_id
            WHEN NOT MATCHED THEN
                INSERT (merkle_proof_id, transaction_id, group_idx, tree_size, hashes)
                VALUES (x.merkle_proof_id, x.transaction_id, x.group_idx, x.tree_size, x.hashes)
            """.trimIndent()
        }

    override val persistMerkleProofLeaves: (batchSize: Int) -> String
        get() = { batchSize ->
            """
            MERGE INTO utxo_transaction_merkle_proof_leaves AS utmpl
            USING (VALUES${List(batchSize) { "(?, ?)" }.joinToString(",")})
                AS x(merkle_proof_id, leaf_index)
            ON utmpl.merkle_proof_id = x.merkle_proof_id
            AND utmpl.leaf_index = x.leaf_index
            WHEN NOT MATCHED THEN
                INSERT (merkle_proof_id, leaf_index)
                VALUES (x.merkle_proof_id, x.leaf_index)
            """.trimIndent()
        }
}
