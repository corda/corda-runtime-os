package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.mapTuples
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

class UtxoRepositoryImpl(
    private val serializationService: SerializationService,
    private val wireTransactionFactory: WireTransactionFactory,
    private val digestService: DigestService
) : UtxoRepository {

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
            VALUES (:id, :privacySalt, :accountId, :createdAt)"""
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
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
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
            INSERT INTO {h-schema}utxo_transaction_signature(transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (:transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.encoded.hashAsString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun ByteArray.hashAsString() =
        digestService.hash(this, DigestAlgorithmName.SHA2_256).toString()

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
