package net.corda.ledger.persistence.impl.internal

import net.corda.data.persistence.EntityResponse
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.CPK_IDENTIFIERS_KEY
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Tuple


class ConsensualLedgerRepository(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService,
    private val serializationService: SerializationService
) {
    companion object {
        private val logger = contextLogger()
        // TODO These values are used instead of missing values
        val fakePublicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        val fakeContext = emptyMap<String, String>()
        val fakeSignature = DigitalSignature.WithKey(fakePublicKey, "0".toByteArray(), fakeContext)
        val fakeDigitalSignatureMetadata =
            DigitalSignatureMetadata(Instant.now(), mapOf()) //CORE-5091 populate this properly...
        val fakeSignatureWithMetaData = DigitalSignatureAndMetadata(fakeSignature, fakeDigitalSignatureMetadata)
    }

    fun persistTransaction(entityManager: EntityManager, transaction: ConsensualSignedTransactionImpl, account :String): EntityResponse {
        val transactionId = transaction.id.toHexString()
        val wireTransaction = transaction.wireTransaction
        val now = Instant.now()
        persistTransaction(entityManager, now, wireTransaction, account)
        wireTransaction.componentGroupLists.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexed { leafIndex, bytes ->
                persistComponentLeaf(entityManager, now, transactionId, groupIndex, leafIndex, bytes)
            }
        }
        persistTransactionStatus(entityManager, now, transactionId, "Faked") // TODO where to get the status from
        // TODO when and what do we write to the CPKs table?
        persistCpk(entityManager, now, wireTransaction)
        persistTransactionCpk(entityManager, transactionId)

        transaction.signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
            persistSignature(entityManager, now, transactionId, index, digitalSignatureAndMetadata.signature.bytes)
        }

        return EntityResponse(emptyList())
    }

    fun findTransaction(entityManager: EntityManager, id: String): ConsensualSignedTransactionImpl? {

        val rows = entityManager.createNativeQuery(
            """
                SELECT tx.id, tx.privacy_salt, tx.account_id, tx.created, txc.group_idx, txc.leaf_idx, txc.data, txc.hash
                FROM {h-schema}consensual_transaction AS tx
                JOIN {h-schema}consensual_transaction_component AS txc ON tx.id = txc.transaction_id
                WHERE tx.id = :id
                ORDER BY txc.group_idx, txc.leaf_idx
                """
        )
            .setParameter("id", id)
            .resultList

        if (rows.isEmpty()) return null

        val firstRowColumns = rows.first() as Array<*>
        val privacySalt = PrivacySaltImpl(firstRowColumns[1] as ByteArray)
        val componentGroupLists = queryRowsToComponentGroupLists(rows)
        val wireTransaction = WireTransaction(merkleTreeFactory, digestService, jsonMarshallingService, privacySalt, componentGroupLists)
        return ConsensualSignedTransactionImpl(
            serializationService,
            wireTransaction,
            findSignatures(entityManager, id))
    }

    @VisibleForTesting
    internal fun queryRowsToComponentGroupLists(rows: List<Any?>): List<List<ByteArray>> {
        val componentGroupLists: MutableList<MutableList<ByteArray>> = mutableListOf()
        var componentsList: MutableList<ByteArray> = mutableListOf()
        var expectedGroupIdx = 0
        rows.forEach {
            val columns = it as Array<*>
            val groupIdx = (columns[4] as Number).toInt()   // txc.group_idx
            val leafIdx = (columns[5] as Number).toInt()    // txc.leaf_idx
            val data = columns[6] as ByteArray              // txc.data
            while (groupIdx > expectedGroupIdx) {
                // add empty lists for skipped group indices
                componentGroupLists.add(componentsList)
                componentsList = mutableListOf()
                expectedGroupIdx++
            }
            check(componentsList.size == leafIdx) {
                val id = columns[0] as String   // tx.id
                "Missing data for transaction with ID: $id, groupIdx: $groupIdx, leafIdx: ${componentsList.size}"
            }
            componentsList.add(data)
        }
        componentGroupLists.add(componentsList)
        return componentGroupLists
    }

    private fun findSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createNativeQuery(
            """
                SELECT signature
                FROM {h-schema}consensual_transaction_signature
                WHERE transaction_id = :transactionId
                ORDER BY signature_idx""",
            Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultList
            .map { it as Tuple }
            .map { r ->
                DigitalSignatureAndMetadata(
                    // TODO where to get public key and context from (it's not in DB)?
                    DigitalSignature.WithKey(fakePublicKey, r.get(0) as ByteArray, fakeContext),
                    // TODO where to get digital signature metadata from?
                    fakeDigitalSignatureMetadata)
                    //DigitalSignatureMetadata(Instant.now(), emptyMap()))
            }
    }

    private fun persistTransaction(entityManager: EntityManager, timestamp: Instant, tx: WireTransaction, account: String) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
                VALUES (:id, :privacySalt, :accountId, :createdAt)"""
        )
            .setParameter("id", tx.id.toHexString())
            .setParameter("privacySalt", tx.privacySalt.bytes)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    @Suppress("LongParameterList")
    private fun persistComponentLeaf(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray
    ) {
        val dataDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        val hash = dataDigest.digest(data).toHexString()

        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
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

    private fun persistTransactionStatus(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        status: String
    ) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, created)
                VALUES (:txId, :status, :createdAt)"""
        )
            .setParameter("txId", transactionId)
            .setParameter("status", status)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun persistCpk(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: WireTransaction
    ) {
        // TODO get values from transaction metadata
        val cpkIdentifiers = tx.metadata.get(CPK_IDENTIFIERS_KEY)
        logger.info("cpkIdentifiers = [$cpkIdentifiers]")
        val fileHash = SecureHash.parse("SHA-256:1234567890123456")
        val name = "cpk-name"
        val signerHash = SecureHash.parse("SHA-256:0000000000000000")
        val version = 1
        val data = ByteArray(10000)

        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_cpk(file_hash, name, signer_hash, version, data, created)
                VALUES (:fileHash, :name, :signerHash, :version, :data, :createdAt) ON CONFLICT DO NOTHING""")
            .setParameter("fileHash", fileHash.toHexString())
            .setParameter("name", name)
            .setParameter("signerHash", signerHash.toHexString())
            .setParameter("version", version)
            .setParameter("data", data)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String
    ) {
        // TODO get values from transaction metadata
        val fileHash = SecureHash.parse("SHA-256:1234567890123456")
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_cpk(transaction_id, file_hash)
                VALUES (:transactionId, :fileHash)""")
            .setParameter("transactionId", transactionId)
            .setParameter("fileHash", fileHash.toHexString())
            .executeUpdate()
    }

    private fun persistSignature(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        index: Int,
        signature: ByteArray
    ) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_signature(transaction_id, signature_idx, signature, created)
                VALUES (:transactionId, :signatureIdx, :signature, :createdAt)""")
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", signature)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

}