package net.corda.ledger.persistence.utxo.impl

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.ComponentLeafDto
import net.corda.ledger.persistence.common.mapToComponentGroups
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.utxo.datamodel.UtxoGroupParametersEntity
import net.corda.ledger.utxo.datamodel.UtxoTransactionComponentEntity
import net.corda.ledger.utxo.datamodel.UtxoTransactionComponentEntityId
import net.corda.ledger.utxo.datamodel.UtxoTransactionEntity
import net.corda.ledger.utxo.datamodel.UtxoTransactionOutputEntity
import net.corda.ledger.utxo.datamodel.UtxoTransactionOutputEntityId
import net.corda.ledger.utxo.datamodel.UtxoTransactionSignatureEntity
import net.corda.ledger.utxo.datamodel.UtxoTransactionSignatureEntityId
import net.corda.ledger.utxo.datamodel.UtxoTransactionSourceEntity
import net.corda.ledger.utxo.datamodel.UtxoTransactionSourceEntityId
import net.corda.ledger.utxo.datamodel.UtxoTransactionStatusEntity
import net.corda.ledger.utxo.datamodel.UtxoVisibleTransactionStateEntity
import net.corda.ledger.utxo.datamodel.UtxoVisibleTransactionStateEntityId
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
import javax.persistence.Tuple

@Suppress("TooManyFunctions")
/**
 * Reads and writes ledger transaction data to and from the virtual node vault database.
 * The component only exists to be created inside a PERSISTENCE sandbox. We denote it
 * as "corda.marker.only" to force the sandbox to create it, despite it implementing
 * only the [UsedByPersistence] marker interface.
 */
@Component(
    service = [ UtxoRepository::class, UsedByPersistence::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE
)
class UtxoRepositoryImpl @Activate constructor(
    @Reference
    private val serializationService: SerializationService,
    @Reference
    private val wireTransactionFactory: WireTransactionFactory
) : UtxoRepository, UsedByPersistence {
    private companion object {
        private val UNVERIFIED = TransactionStatus.UNVERIFIED.value
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        return entityManager.createQuery(
            "SELECT privacySalt FROM UtxoTransactionEntity WHERE id = :txId",
            ByteArray::class.java
        )
            .setParameter("txId", transactionId)
            .resultList
            .map { PrivacySaltImpl(it) }
            .firstOrNull()

        /*return entityManager.createNativeQuery(
            """
                SELECT privacy_salt
                FROM {h-schema}utxo_transaction
                WHERE id = :transactionId""",
            Tuple::class.java
        )
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> PrivacySaltImpl(r.get(0) as ByteArray) }
            .firstOrNull()*/
    }

    override fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String
    ): Map<Int, List<ByteArray>> {

        return entityManager.createQuery("""
                SELECT groupIndex, leafIndex, data
                FROM UtxoTransactionComponentEntity
                WHERE transactionId = :txId
                ORDER BY groupIndex, leafIndex""",
            Tuple::class.java
        )
            .setParameter("txId", transactionId)
            .resultList
            .mapToComponentGroups(UtxoComponentGroupMapper(transactionId))

        /*logger.info("Component results = ${query.mapToComponentGroups(UtxoComponentGroupMapper(transactionId))}")

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
            .mapToComponentGroups(UtxoComponentGroupMapper(transactionId))*/
    }

    override fun findUnconsumedVisibleStatesByType(
        entityManager: EntityManager,
        groupIndices: List<Int>
    ):  List<ComponentLeafDto> {
        return entityManager.createQuery("""
                SELECT tc.transactionId, tc.groupIndex, tc.leafIndex, tc.data
                FROM UtxoTransactionComponentEntity AS tc
                JOIN UtxoVisibleTransactionStateEntity AS rts
                    ON rts.transactionId = tc.transactionId
                    AND rts.leafIndex = tc.leafIndex
                JOIN UtxoTransactionStatusEntity AS ts
                    ON ts.transactionId = tc.transactionId
                WHERE tc.groupIndex IN (:groupIndices)
                AND rts.consumed IS NULL
                AND ts.status = :verified
                ORDER BY tc.groupIndex, tc.leafIndex""",
            Tuple::class.java
        )
            .setParameter("groupIndices", groupIndices)
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .resultList
            .map { t ->
                ComponentLeafDto(
                    t[0] as String, // transactionId
                    (t[1] as Number).toInt(), // groupIndex
                    (t[2] as Number).toInt(), // leafIndex
                    t[3] as ByteArray // data
                )
            }

        /*logger.info("Results = ${query.map {it.get(0).toString() + "," + it.get(1) + "," + it.get(2) + "," + it.get(3) }}")

        return entityManager.createNativeQuery(
            """
                SELECT tc.transaction_id, tc.group_idx, tc.leaf_idx, tc.data
                FROM {h-schema}utxo_transaction_component AS tc
                JOIN {h-schema}utxo_visible_transaction_state AS rts
                    ON rts.transaction_id = tc.transaction_id
                    AND rts.leaf_idx = tc.leaf_idx
                JOIN {h-schema}utxo_transaction_status AS ts
                    ON ts.transaction_id = tc.transaction_id
                WHERE tc.group_idx IN (:groupIndices)
                AND rts.consumed IS NULL
                AND ts.status = :verified
                ORDER BY tc.group_idx, tc.leaf_idx""",
            Tuple::class.java
        )
            .setParameter("groupIndices", groupIndices)
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .resultListAsTuples()
            .map { t ->
                ComponentLeafDto(
                    t[0] as String, // transactionId
                    (t[1] as Number).toInt(), // groupIndex
                    (t[2] as Number).toInt(), // leafIndex
                    t[3] as ByteArray // data
                )
            }*/
    }

    override fun resolveStateRefs(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        groupIndices: List<Int>
    ): List<ComponentLeafDto> {
        return entityManager.createQuery(
            """
                SELECT tc.transactionId, tc.groupIndex, tc.leafIndex, tc.data
                FROM UtxoTransactionComponentEntity AS tc
                JOIN UtxoTransactionStatusEntity AS ts
                    ON ts.transactionId = tc.transactionId
                WHERE tc.groupIndex IN (:groupIndices)
                AND tc.transactionId in (:transactionIds)
                AND (tc.transactionId||':'|| tc.leafIndex) in (:stateRefs)
                AND ts.status = :verified
                ORDER BY tc.groupIndex, tc.leafIndex""",
            Tuple::class.java
        )
            .setParameter("groupIndices", groupIndices)
            .setParameter(
                "transactionIds",
                stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map { it.toString() })
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .resultList
            .map { t ->
                ComponentLeafDto(
                    t[0] as String, // transactionId
                    (t[1] as Number).toInt(), // groupIndex
                    (t[2] as Number).toInt(), // leafIndex
                    t[3] as ByteArray // data
                )
            }
    }

    override fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createQuery(
            """
                SELECT signature
                FROM UtxoTransactionSignatureEntity
                WHERE transactionId = :transactionId
                ORDER BY signature_idx""",
            ByteArray::class.java
        )
            .setParameter("transactionId", transactionId)
            .resultList
            .map { r -> serializationService.deserialize(r) }
    }

    override fun findTransactionStatus(entityManager: EntityManager, id: String): String? {
        return entityManager.createQuery(
            """
                SELECT status
                FROM UtxoTransactionStatusEntity
                WHERE transactionId = :transactionId
                """,
            String::class.java
        )
            .setParameter("transactionId", id)
            .resultList
            .singleOrNull()
    }

    override fun markTransactionVisibleStatesConsumed(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        timestamp: Instant
    ) {
        entityManager.createQuery(
        """
            UPDATE UtxoVisibleTransactionStateEntity
            SET consumed = :consumed
            WHERE transactionId in (:transactionIds)
            AND (transactionId || ':' || leafIndex) IN (:stateRefs)"""
        )
            .setParameter("consumed", timestamp)
            .setParameter("transactionIds", stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map { it.toString() })
            .executeUpdate()
    }

    override fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    ) {
        try {
            if ( entityManager.find(UtxoTransactionEntity::class.java, id) == null ) {
                entityManager.persist(UtxoTransactionEntity(id, privacySalt, account, timestamp))
            }
        } catch (e: EntityExistsException) {
            0.logResult("transaction [$id]")
        }
        /*entityManager.createNativeQuery(
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
            .logResult("transaction [$id]")*/
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
        try {
            if ( entityManager.find(
                    UtxoTransactionComponentEntity::class.java,
                    UtxoTransactionComponentEntityId(transactionId, groupIndex, leafIndex)) == null ) {
                entityManager.persist(UtxoTransactionComponentEntity(
                    transactionId,
                    groupIndex,
                    leafIndex,
                    data,
                    hash,
                    timestamp))
            }
        } catch (e: EntityExistsException) {
            0.logResult("transaction component [$transactionId, $groupIndex, $leafIndex]")
        }
        /*
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
            .logResult("transaction component [$transactionId, $groupIndex, $leafIndex]")*/
    }

    override fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        fileChecksums: Collection<String>
    ) {
        // NOT CURRENTLY USED - NO ENTITY DEFINITION, NEEDS REVIEW
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
            .logResult("transaction CPK [$transactionId, $fileChecksums]")
    }

    override fun persistTransactionOutput(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        type: String,
        tokenType: String?,
        tokenIssuerHash: String?,
        tokenNotaryX500Name: String?,
        tokenSymbol: String?,
        tokenTag: String?,
        tokenOwnerHash: String?,
        tokenAmount: BigDecimal?,
        timestamp: Instant
    ) {
        try {
            if ( entityManager.find(
                    UtxoTransactionOutputEntity::class.java,
                    UtxoTransactionOutputEntityId(transactionId, groupIndex, leafIndex)) == null ) {
                entityManager.persist(UtxoTransactionOutputEntity(
                    transactionId,
                    groupIndex,
                    leafIndex,
                    type,
                    tokenType,
                    tokenIssuerHash,
                    tokenNotaryX500Name,
                    tokenSymbol,
                    tokenTag,
                    tokenOwnerHash,
                    tokenAmount,
                    timestamp))
            }
        } catch (e: EntityExistsException) {
            0.logResult("transaction output [$transactionId, $groupIndex, $leafIndex]")
        }
    }

    override fun persistTransactionVisibleStates(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        consumed: Boolean,
        customRepresentation: CustomRepresentation,
        timestamp: Instant,
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_visible_transaction_state(
                transaction_id, group_idx, leaf_idx, custom_representation, created, consumed
            )
            VALUES(
                :transactionId, 
                :groupIndex, 
                :leafIndex, 
                CAST(:custom_representation as JSONB), 
                :createdAt, 
                ${if (consumed) ":consumedAt" else "null"}
            )
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("custom_representation", customRepresentation.json)
            .setParameter("createdAt", timestamp)
            .run { if (consumed) setParameter("consumedAt", timestamp) else this }
            .executeUpdate()
            .logResult("transaction relevancy [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    ) {
        try {
            if ( entityManager.find(
                    UtxoTransactionSignatureEntity::class.java,
                    UtxoTransactionSignatureEntityId(transactionId, index)) == null) {
                entityManager.persist(UtxoTransactionSignatureEntity(
                    transactionId,
                    index,
                    serializationService.serialize(signature).bytes,
                    signature.by.toString(),
                    timestamp)
                )
            }
        } catch (e: EntityExistsException) {
            0.logResult("transaction signature [$transactionId, $index]")
        }
        /*entityManager.createNativeQuery(
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
            .setParameter("publicKeyHash", signature.by.toString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction signature [$transactionId, $index]")*/
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
        try {
            if ( entityManager.find(
                    UtxoTransactionSourceEntity::class.java,
                    UtxoTransactionSourceEntityId(transactionId, groupIndex, leafIndex)) == null) {
                entityManager.persist(UtxoTransactionSourceEntity(
                    transactionId,
                    groupIndex,
                    leafIndex,
                    refTransactionId,
                    refLeafIndex,
                    isRefInput,
                    timestamp)
                )
            }
        } catch (e: EntityExistsException) {
            0.logResult("transaction source [$transactionId, $groupIndex, $leafIndex]")
        }
        /*entityManager.createNativeQuery(
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
            .logResult("transaction source [$transactionId, $groupIndex, $leafIndex]")*/
    }

    override fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant
    ) {
        if (transactionStatus == TransactionStatus.UNVERIFIED) {
            try {
                if (entityManager.find(UtxoTransactionStatusEntity::class.java, transactionId) == null) {
                    entityManager.persist(UtxoTransactionStatusEntity(
                        transactionId,
                        transactionStatus.value,
                        timestamp)
                    )
                }
            } catch (e: EntityExistsException) {
                0.logResult("transaction [$transactionId]")
            }
        } else {
            val rowsUpdated = entityManager.createQuery("""
                UPDATE UtxoTransactionStatusEntity
                SET status = :status, updated = :updatedAt
                WHERE transactionId = :transactionId AND status = 'U'"""
            )
                .setParameter("transactionId", transactionId)
                .setParameter("status", transactionStatus.value)
                .setParameter("updatedAt", timestamp)
                .executeUpdate()
                .logResult("transaction status [$transactionId, ${transactionStatus.value}]")
            // Insert/update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
            /*val rowsUpdated = entityManager.createNativeQuery(
                """
            INSERT INTO {h-schema}utxo_transaction_status(transaction_id, status, updated)
            VALUES (:transactionId, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE utxo_transaction_status.status = EXCLUDED.status OR utxo_transaction_status.status = '$UNVERIFIED'"""
            )
                .setParameter("transactionId", transactionId)
                .setParameter("status", transactionStatus.value)
                .setParameter("updatedAt", timestamp)
                .executeUpdate()
                .logResult("transaction status [$transactionId, ${transactionStatus.value}]")*/

            check(rowsUpdated == 1) {
                // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
                "Existing status for transaction with ID $transactionId can't be updated to $transactionStatus"
            }
        }
    }

    override fun findSignedGroupParameters(entityManager: EntityManager, hash: String): SignedGroupParameters? {
        return entityManager.createQuery(
            """
                SELECT
                    parameters,
                    signaturePublicKey,
                    signatureContent,
                    signatureSpec
                FROM UtxoGroupParametersEntity
                WHERE hash = :hash""",
            Tuple::class.java
        )
            .setParameter("hash", hash)
            .resultList
            .map { r ->
                SignedGroupParameters(
                    ByteBuffer.wrap(r.get(0) as ByteArray),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(r.get(1) as ByteArray),
                        ByteBuffer.wrap(r.get(2) as ByteArray)
                    ),
                    CryptoSignatureSpec((r.get(3) as String), null, null)
                )
            }
            .singleOrNull()
    }

    override fun persistSignedGroupParameters(
        entityManager: EntityManager,
        hash: String,
        signedGroupParameters: SignedGroupParameters,
        timestamp: Instant
    ) {
        try {
            if (entityManager.find(UtxoGroupParametersEntity::class.java, hash) == null) {
                entityManager.persist(UtxoGroupParametersEntity(
                    hash,
                    signedGroupParameters.groupParameters.array(),
                    signedGroupParameters.mgmSignature.publicKey.array(),
                    signedGroupParameters.mgmSignature.bytes.array(),
                    signedGroupParameters.mgmSignatureSpec.signatureName,
                    timestamp)
                )
            }
        } catch (e: EntityExistsException) {
            0.logResult("signed group parameters [$hash]")
        }
    }

    private fun Int.logResult(entity: String): Int {
        if (this == 0) {
            logger.debug {
                "UTXO ledger entity not persisted due to existing row in database. Entity: $entity"
            }
        }
        return this
    }
}
