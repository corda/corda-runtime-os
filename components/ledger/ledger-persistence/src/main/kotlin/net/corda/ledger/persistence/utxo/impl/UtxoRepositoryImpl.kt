package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.mapTuples
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.DigestAlgorithmName
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

class UtxoRepositoryImpl(
    private val serializationService: SerializationService,
    private val wireTransactionFactory: WireTransactionFactory,
    private val digestService: DigestService
) : UtxoRepository {
    private companion object {
        private val UNVERIFIED = TransactionStatus.UNVERIFIED.value
    }

    override fun findTransaction(
        entityManager: EntityManager,
        id: String
    ): SignedTransactionContainer? {
        val privacySalt = findTransactionPrivacySalt(entityManager, id) ?: return null
        val wireTransaction = wireTransactionFactory.create(
            findTransactionComponentLeafs(entityManager, id),
            privacySalt
        )
        return SignedTransactionContainer(
            wireTransaction,
            findTransactionSignatures(entityManager, id)
        )
    }

    private fun findTransactionPrivacySalt(
        entityManager: EntityManager,
        transactionId: String
    ): PrivacySaltImpl? {
        return entityManager.createNativeQuery(
            """
                SELECT privacy_salt
                FROM {h-schema}utxo_transaction
                WHERE id = :transactionId""",
            Tuple::class.java
        )
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> PrivacySaltImpl(r.get(0) as ByteArray) }
            .firstOrNull()
    }

    override fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String
    ): List<List<ByteArray>> {
        return entityManager.createNativeQuery(
            """
                SELECT group_idx, leaf_idx, data
                FROM {h-schema}utxo_transaction_component
                WHERE transaction_id = :transactionId
                ORDER BY group_idx, leaf_idx""",
            Tuple::class.java
        )
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .mapTuples(ComponentGroupListsTuplesMapper(transactionId))
    }

    override fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createNativeQuery(
            """
                SELECT signature
                FROM {h-schema}utxo_transaction_signature
                WHERE transaction_id = :transactionId
                ORDER BY signature_idx""",
            Tuple::class.java
        )
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> serializationService.deserialize(r.get(0) as ByteArray) }
    }

    override fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionComponentLeaf(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray,
        hash: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        fileChecksums: Collection<String>
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}utxo_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("fileChecksums", fileChecksums)
            .executeUpdate()
    }

    override fun persistTransactionOutput(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        type: String,
        tokenType: String,
        tokenIssuerHash: String,
        tokenNotaryX500Name: String,
        tokenSymbol: String,
        tokenTag: String,
        tokenOwnerHash: String,
        tokenAmount: BigDecimal,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_output(
                transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                token_symbol, token_tag, token_owner_hash, token_amount, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :type, :tokenType, :tokenIssuerHash, :tokenNotaryX500Name,
                :tokenSymbol, :tokenTag, :tokenOwnerHash, :tokenAmount, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("type", type)
            .setParameter("tokenType", tokenType)
            .setParameter("tokenIssuerHash", tokenIssuerHash)
            .setParameter("tokenNotaryX500Name", tokenNotaryX500Name)
            .setParameter("tokenSymbol", tokenSymbol)
            .setParameter("tokenTag", tokenTag)
            .setParameter("tokenOwnerHash", tokenOwnerHash)
            .setParameter("tokenAmount", tokenAmount)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionRelevancy(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        relevant: Boolean,
        consumed: Boolean,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_output(
                transaction_id, group_idx, leaf_idx, is_relevant, consumed, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :relevant, :consumed, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("relevant", relevant)
            .setParameter("consumed", consumed)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_signature(
                transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (
                :transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.encoded.hashAsString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionSource(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        refTransactionId: String,
        refLeafIndex: Int,
        isRefInput: Boolean,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_sources(
                transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :refTransactionId, :refLeafIndex, :isRefInput, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("refTransactionId", refTransactionId)
            .setParameter("refLeafIndex", refLeafIndex)
            .setParameter("isRefInput", isRefInput)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        status: TransactionStatus,
        timestamp: Instant
    ) {
        // Insert/update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
        val rowsUpdated = entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_status(transaction_id, status, updated)
            VALUES (:transactionId, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE utxo_transaction_status.status = EXCLUDED.status OR utxo_transaction_status.status = '$UNVERIFIED'"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("status", status.value)
            .setParameter("updatedAt", timestamp)
            .executeUpdate()

        check(rowsUpdated == 1 || status == TransactionStatus.UNVERIFIED) {
            // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
            "Existing status for transaction with ID $transactionId can't be updated to $status (illegal state that shouldn't happen)"
        }
    }

    private fun ByteArray.hashAsString() =
        digestService.hash(this, DigestAlgorithmName.SHA2_256).toString()

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
