package net.corda.ledger.persistence.utxo.impl

import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ UtxoQueryProvider::class ])
class PostgresUtxoQueryProvider @Activate constructor(
    @Reference(target = POSTGRES_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
): AbstractUtxoQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }

    override val persistTransaction: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionComponentLeaf: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionCpk: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}utxo_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionOutput: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_output(
                transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                token_symbol, token_tag, token_owner_hash, token_amount, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :type, :tokenType, :tokenIssuerHash, :tokenNotaryX500Name,
                :tokenSymbol, :tokenTag, :tokenOwnerHash, :tokenAmount, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override fun persistTransactionVisibleStates(consumed: Boolean): String {
        return """
            INSERT INTO {h-schema}utxo_visible_transaction_state(
                transaction_id, group_idx, leaf_idx, custom_representation, created, consumed
            ) VALUES(
                :transactionId, 
                :groupIndex, 
                :leafIndex, 
                CAST(:custom_representation as JSONB), 
                :createdAt, 
                ${if (consumed) ":consumedAt" else "null"}
            ) ON CONFLICT DO NOTHING"""
            .trimIndent()
    }

    override val persistTransactionSignature: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_signature(
                transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (
                :transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionSource: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_sources(
                transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :refTransactionId, :refLeafIndex, :isRefInput, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionStatus: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_status(transaction_id, status, updated)
            VALUES (:transactionId, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE utxo_transaction_status.status = EXCLUDED.status OR utxo_transaction_status.status = '$UNVERIFIED'"""
            .trimIndent()

    override val persistSignedGroupParameters: String
        get() = """
            INSERT INTO {h-schema}utxo_group_parameters(
                hash, parameters, signature_public_key, signature_content, signature_spec, created)
            VALUES (
                :hash, :parameters, :signature_public_key, :signature_content, :signature_spec, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()
}
