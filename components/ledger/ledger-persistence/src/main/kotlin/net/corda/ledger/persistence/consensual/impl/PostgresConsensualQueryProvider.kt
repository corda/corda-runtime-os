package net.corda.ledger.persistence.consensual.impl

import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ ConsensualQueryProvider::class ])
class PostgresConsensualQueryProvider @Activate constructor(
    @Reference(target = POSTGRES_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
) : AbstractConsensualQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).debug { "Activated for ${databaseTypeProvider.databaseType}" }
    }

    override val persistTransaction: String
        get() = """
            INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionComponentLeaf: String
        get() = """
            INSERT INTO {h-schema}consensual_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionStatus: String
        get() = """
            INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, updated)
            VALUES (:id, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE consensual_transaction_status.status = EXCLUDED.status OR consensual_transaction_status.status = '$UNVERIFIED'"""
            .trimIndent()

    override val persistTransactionSignature: String
        get() = """
            INSERT INTO {h-schema}consensual_transaction_signature(transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (:transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionCpk: String
        get() = """
            INSERT INTO {h-schema}consensual_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}consensual_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
            .trimIndent()
}
