package net.corda.testing.ledger.utxo

import net.corda.ledger.persistence.utxo.impl.AbstractUtxoQueryProvider
import net.corda.ledger.persistence.utxo.impl.UtxoQueryProvider
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.HSQLDB_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ UtxoQueryProvider::class ])
class HsqldbUtxoQueryProvider @Activate constructor(
    @Reference(target = HSQLDB_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
): AbstractUtxoQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }

    override val persistTransaction: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction AS ut
            USING (VALUES :id, CAST(:privacySalt AS VARBINARY(64)), :accountId, CAST(:createdAt AS TIMESTAMP))
                AS x(id, privacy_salt, account_id, created)
            ON x.id = ut.id
            WHEN NOT MATCHED THEN
                INSERT (id, privacy_salt, account_id, created)
                VALUES (x.id, x.privacy_salt, x.account_id, x.created)"""
            .trimIndent()

    override val persistTransactionComponentLeaf: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_component AS utc
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT), CAST(:data AS VARBINARY(1048576)), :hash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, data, hash, created)
            ON x.transaction_id = utc.transaction_id AND x.group_idx = utc.group_idx AND x.leaf_idx = utc.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, data, hash, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.data, x.hash, x.created)"""
            .trimIndent()

    override val persistTransactionCpk: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_cpk AS utc
            USING (SELECT :transactionId, file_checksum
                   FROM {h-schema}utxo_cpk
                   WHERE file_checksum IN (:fileChecksums)) AS x(transaction_id, file_checksum)
            ON x.transaction_id = utc.transaction_id AND x.file_checksum = utc.file_checksum
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, file_checksum)
                VALUES (x.transaction_id, x.file_checksum)"""
            .trimIndent()

    override val persistTransactionOutput: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_output AS uto
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT), :type, :tokenType, :tokenIssuerHash,
                          :tokenSymbol, :tokenTag, :tokenOwnerHash, :tokenAmount, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash,
                     token_symbol, token_tag, token_owner_hash, token_amount, created)
            ON uto.transaction_id = x.transaction_id AND uto.group_idx = x.group_idx AND uto.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash,
                        token_symbol, token_tag, token_owner_hash, token_amount, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.type, x.token_type, x.token_issuer_hash,
                        x.token_symbol, x.token_tag, x.token_owner_hash, x.token_amount, x.created)"""
            .trimIndent()

    override fun persistTransactionVisibleStates(consumed: Boolean): String {
        return """
            MERGE INTO {h-schema}utxo_visible_transaction_state AS uvts
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT), :custom_representation,
                          CAST(:createdAt AS TIMESTAMP), ${if (consumed) "CAST(:consumedAt AS TIMESTAMP)" else "null"})
                AS x(transaction_id, group_idx, leaf_idx, custom_representation, created, consumed)
            ON uvts.transaction_id = x.transaction_id AND uvts.group_idx = x.group_idx AND uvts.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, custom_representation, created, consumed)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.custom_representation, x.created, x.consumed)"""
            .trimIndent()
    }

    override val persistTransactionSignature: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_signature AS uts
            USING (VALUES :transactionId, CAST(:signatureIdx AS INT), CAST(:signature AS VARBINARY(1048576)),
                          :publicKeyHash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, signature_idx, signature, pub_key_hash, created)
            ON uts.transaction_id = x.transaction_id AND uts.signature_idx = x.signature_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, signature_idx, signature, pub_key_hash, created)
                VALUES (x.transaction_id, x.signature_idx, x.signature, x.pub_key_hash, x.created)"""
            .trimIndent()

    override val persistTransactionSource: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_sources AS uts
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT),
                          :refTransactionId, CAST(:refLeafIndex AS INT), CAST(:isRefInput AS BOOLEAN), CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
            ON uts.transaction_id = x.transaction_id AND uts.group_idx = x.group_idx AND uts.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.ref_transaction_id, x.ref_leaf_idx, x.is_ref_input, x.created)"""
            .trimIndent()

    override val persistTransactionStatus: String
        get() = """
            MERGE INTO {h-schema}utxo_transaction_status AS uts
            USING (VALUES :transactionId, :status, CAST(:updatedAt AS TIMESTAMP)) AS x(transaction_id, status, updated)
            ON uts.transaction_id = x.transaction_id
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, status, updated)
                VALUES (x.transaction_id, x.status, x.updated)
            WHEN MATCHED AND (uts.status = x.status OR uts.status = '$UNVERIFIED') THEN
                UPDATE SET status = x.status, updated = x.updated"""
            .trimIndent()

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
}
