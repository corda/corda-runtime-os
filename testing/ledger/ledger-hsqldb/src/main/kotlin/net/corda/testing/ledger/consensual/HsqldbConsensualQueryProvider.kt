package net.corda.testing.ledger.consensual

import net.corda.ledger.persistence.consensual.impl.ConsensualQueryProvider
import net.corda.ledger.persistence.consensual.impl.AbstractConsensualQueryProvider
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.HSQLDB_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ ConsensualQueryProvider::class ])
class HsqldbConsensualQueryProvider @Activate constructor(
    @Reference(target = HSQLDB_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
) : AbstractConsensualQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }

    override val persistTransaction: String
        get() = """
            MERGE INTO {h-schema}consensual_transaction AS ct
            USING (VALUES :id, CAST(:privacySalt as VARBINARY(64)), :accountId, CAST(:createdAt as TIMESTAMP))
                AS x(id, privacy_salt, account_id, created)
            ON ct.id = x.id
            WHEN NOT MATCHED THEN
                INSERT (id, privacy_salt, account_id, created)
                VALUES (x.id, x.privacy_salt, x.account_id, x.created)"""
            .trimIndent()

    override val persistTransactionComponentLeaf: String
        get() = """
            MERGE INTO {h-schema}consensual_transaction_component AS ctc
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT),
                          CAST(:data AS VARBINARY(1048576)), :hash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, data, hash, created)
            ON ctc.transaction_id = x.transaction_id AND ctc.group_idx = x.group_idx AND ctc.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, data, hash, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.data, x.hash, x.created)"""
            .trimIndent()

    override val persistTransactionStatus: String
        get() = """
            MERGE INTO {h-schema}consensual_transaction_status AS cts
            USING (VALUES :id, :status, CAST(:updatedAt AS TIMESTAMP)) AS x(transaction_id, status, updated)
            ON cts.transaction_id = x.transaction_id
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, status, updated)
                VALUES (x.transaction_id, x.status, x.updated)
            WHEN MATCHED AND (cts.status = x.status OR cts.status = '$UNVERIFIED') THEN
                UPDATE SET status = x.status, updated = x.updated"""
            .trimIndent()

    override val persistTransactionSignature: String
        get() = """
            MERGE INTO {h-schema}consensual_transaction_signature AS cts
            USING (VALUES :transactionId, CAST(:signatureIdx AS INT), CAST(:signature AS VARBINARY(1048576)),
                          :publicKeyHash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, signature_idx, signature, pub_key_hash, created)
            ON cts.transaction_id = x.transaction_id AND cts.signature_idx = x.signature_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, signature_idx, signature, pub_key_hash, created)
                VALUES (x.transaction_id, x.signature_idx, x.signature, x.pub_key_hash, x.created)"""
            .trimIndent()

    override val persistTransactionCpk: String
        get() = """
            MERGE INTO {h-schema}consensual_transaction_cpk AS ctc
            USING (SELECT :transactionId, file_checksum
                   FROM {h-schema}consensual_cpk
                   WHERE file_checksum IN (:fileChecksums)) AS x(transaction_id, file_checksum)
            ON x.transaction_id = ctc.transaction_id AND x.file_checksum = ctc.file_checksum
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, file_checksum)
                VALUES (x.transaction_id, x.file_checksum)"""
            .trimIndent()
}
