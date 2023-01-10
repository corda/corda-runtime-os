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
import net.corda.orm.DatabaseType.HSQLDB
import net.corda.orm.DatabaseTypeProvider
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
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
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
    private val wireTransactionFactory: WireTransactionFactory,
    @Reference(cardinality = OPTIONAL)
    databaseTypeProvider: DatabaseTypeProvider?
) : UtxoRepository, UsedByPersistence {
    private companion object {
        private val UNVERIFIED = TransactionStatus.UNVERIFIED.value
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val databaseType = databaseTypeProvider?.databaseType

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
    ): Map<Int, List<ByteArray>> {
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
            .mapToComponentGroups(UtxoComponentGroupMapper(transactionId))
    }

    override fun findUnconsumedVisibleStatesByType(
        entityManager: EntityManager,
        groupIndices: List<Int>
    ):  List<ComponentLeafDto> {
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
            }
    }

    override fun resolveStateRefs(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        groupIndices: List<Int>
    ): List<ComponentLeafDto> {
        return entityManager.createNativeQuery(
            """
                SELECT tc.transaction_id, tc.group_idx, tc.leaf_idx, tc.data
                FROM {h-schema}utxo_transaction_component AS tc
                JOIN {h-schema}utxo_transaction_status AS ts
                    ON ts.transaction_id = tc.transaction_id
                WHERE tc.group_idx IN (:groupIndices)
                AND tc.transaction_id in (:transactionIds)
                AND (tc.transaction_id||':'|| tc.leaf_idx) in (:stateRefs)
                AND ts.status = :verified
                ORDER BY tc.group_idx, tc.leaf_idx""",
            Tuple::class.java
        )
            .setParameter("groupIndices", groupIndices)
            .setParameter(
                "transactionIds",
                stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map { it.toString() })
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .resultListAsTuples()
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

    override fun findTransactionStatus(entityManager: EntityManager, id: String): String? {
        return entityManager.createNativeQuery(
            """
                SELECT status
                FROM {h-schema}utxo_transaction_status
                WHERE transaction_id = :transactionId
                """,
            Tuple::class.java
        )
            .setParameter("transactionId", id)
            .resultListAsTuples()
            .map { r -> r.get(0) as String }
            .singleOrNull()
    }

    override fun markTransactionVisibleStatesConsumed(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
        """
            UPDATE {h-schema}utxo_visible_transaction_state
            SET consumed = :consumed
            WHERE transaction_id in (:transactionIds)
            AND (transaction_id || ':' || leaf_idx) IN (:stateRefs)"""
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction AS ut
            USING (VALUES :id, CAST(:privacySalt AS VARBINARY(64)), :accountId, CAST(:createdAt AS TIMESTAMP))
                AS x(id, privacy_salt, account_id, created)
            ON x.id = ut.id
            WHEN NOT MATCHED THEN
                INSERT (id, privacy_salt, account_id, created)
                VALUES (x.id, x.privacy_salt, x.account_id, x.created)"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction [$id]")
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction_component AS utc
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT), CAST(:data AS VARBINARY(1048576)), :hash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, data, hash, created)
            ON x.transaction_id = utc.transaction_id AND x.group_idx = utc.group_idx AND x.leaf_idx = utc.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, data, hash, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.data, x.hash, x.created)"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction component [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        fileChecksums: Collection<String>
    ) {
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction_cpk AS utc
            USING (SELECT :transactionId, file_checksum
                   FROM {h-schema}utxo_cpk
                   WHERE file_checksum IN (:fileChecksums)) AS x(transaction_id, file_checksum)
            ON x.transaction_id = utc.transaction_id AND x.file_checksum = utc.file_checksum
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, file_checksum)
                VALUES (x.transaction_id, x.file_checksum)"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}utxo_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction_output AS uto
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT), :type, :tokenType, :tokenIssuerHash, :tokenNotaryX500Name,
                          :tokenSymbol, :tokenTag, :tokenOwnerHash, :tokenAmount, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                     token_symbol, token_tag, token_owner_hash, token_amount, created)
            ON uto.transaction_id = x.transaction_id AND uto.group_idx = x.group_idx AND uto.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                        token_symbol, token_tag, token_owner_hash, token_amount, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.type, x.token_type, x.token_issuer_hash, x.token_notary_x500_name,
                        x.token_symbol, x.token_tag, x.token_owner_hash, x.token_amount, x.created)"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction_output(
                transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                token_symbol, token_tag, token_owner_hash, token_amount, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :type, :tokenType, :tokenIssuerHash, :tokenNotaryX500Name,
                :tokenSymbol, :tokenTag, :tokenOwnerHash, :tokenAmount, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
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
            // This is a workaround for avoiding error when tokenAmount is null, see:
            // https://stackoverflow.com/questions/53648865/postgresql-spring-data-jpa-integer-null-interpreted-as-bytea
            .setParameter("tokenAmount", BigDecimal.ZERO)
            .setParameter("tokenAmount", tokenAmount)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction output [$transactionId, $groupIndex, $leafIndex]")
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_visible_transaction_state AS uvts
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT), :custom_representation,
                          CAST(:createdAt AS TIMESTAMP), ${if (consumed) "CAST(:consumedAt AS TIMESTAMP)" else "null"})
                AS x(transaction_id, group_idx, leaf_idx, custom_representation, created, consumed)
            ON uvts.transaction_id = x.transaction_id AND uvts.group_idx = x.group_idx AND uvts.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, custom_representation, created, consumed)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.custom_representation, x.created, x.consumed)"""
            else -> """
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
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction_signature AS uts
            USING (VALUES :transactionId, CAST(:signatureIdx AS INT), CAST(:signature AS VARBINARY(1048576)),
                          :publicKeyHash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, signature_idx, signature, pub_key_hash, created)
            ON uts.transaction_id = x.transaction_id AND uts.signature_idx = x.signature_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, signature_idx, signature, pub_key_hash, created)
                VALUES (x.transaction_id, x.signature_idx, x.signature, x.pub_key_hash, x.created)"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction_signature(
                transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (
                :transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.toString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction signature [$transactionId, $index]")
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction_sources AS uts
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT),
                          :refTransactionId, CAST(:refLeafIndex AS INT), CAST(:isRefInput AS BOOLEAN), CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
            ON uts.transaction_id = x.transaction_id AND uts.group_idx = x.group_idx AND uts.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.ref_transaction_id, x.ref_leaf_idx, x.is_ref_input, x.created)"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction_sources(
                transaction_id, group_idx, leaf_idx, ref_transaction_id, ref_leaf_idx, is_ref_input, created)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :refTransactionId, :refLeafIndex, :isRefInput, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("refTransactionId", refTransactionId)
            .setParameter("refLeafIndex", refLeafIndex)
            .setParameter("isRefInput", isRefInput)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction source [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant
    ) {
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_transaction_status AS uts
            USING (VALUES :transactionId, :status, CAST(:updatedAt AS TIMESTAMP)) AS x(transaction_id, status, updated)
            ON uts.transaction_id = x.transaction_id
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, status, updated)
                VALUES (x.transaction_id, x.status, x.updated)
            WHEN MATCHED AND (uts.status = x.status OR uts.status = '$UNVERIFIED') THEN
                UPDATE SET status = x.status, updated = x.updated"""
            else -> """
            INSERT INTO {h-schema}utxo_transaction_status(transaction_id, status, updated)
            VALUES (:transactionId, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE utxo_transaction_status.status = EXCLUDED.status OR utxo_transaction_status.status = '$UNVERIFIED'"""
        }.trimIndent()
        // Insert/update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
        val rowsUpdated = entityManager.createNativeQuery(nativeSQL)
            .setParameter("transactionId", transactionId)
            .setParameter("status", transactionStatus.value)
            .setParameter("updatedAt", timestamp)
            .executeUpdate()
            .logResult("transaction status [$transactionId, ${transactionStatus.value}]")

        check(rowsUpdated == 1 || transactionStatus == TransactionStatus.UNVERIFIED) {
            // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
            "Existing status for transaction with ID $transactionId can't be updated to $transactionStatus"
        }
    }

    override fun findSignedGroupParameters(entityManager: EntityManager, hash: String): SignedGroupParameters? {
        return entityManager.createNativeQuery(
            """
                SELECT
                    parameters,
                    signature_public_key,
                    signature_content,
                    signature_spec
                FROM {h-schema}utxo_group_parameters
                WHERE hash = :hash""",
            Tuple::class.java
        )
            .setParameter("hash", hash)
            .resultListAsTuples()
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}utxo_group_parameters AS ugp
            USING (VALUES :hash, CAST(:parameters AS VARBINARY(1048576)),
                          CAST(:signature_public_key AS VARBINARY(1048576)), CAST(:signature_content AS VARBINARY(1048576)),
                          :signature_spec, CAST(:createdAt AS TIMESTAMP))
                AS x(hash, parameters, signature_public_key, signature_content, signature_spec, created)
            ON ugp.hash = x.hash
            WHEN NOT MATCHED THEN
                INSERT (hash, parameters, signature_public_key, signature_content, signature_spec, created)
                VALUES (x.hash, x.parameters, x.signature_public_key, x.signature_content, x.signature_spec, x.created)"""
            else -> """
            INSERT INTO {h-schema}utxo_group_parameters(
                hash, parameters, signature_public_key, signature_content, signature_spec, created)
            VALUES (
                :hash, :parameters, :signature_public_key, :signature_content, :signature_spec, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
            .setParameter("hash", hash)
            .setParameter("parameters", signedGroupParameters.groupParameters.array())
            .setParameter("signature_public_key", signedGroupParameters.mgmSignature.publicKey.array())
            .setParameter("signature_content", signedGroupParameters.mgmSignature.bytes.array())
            .setParameter("signature_spec", signedGroupParameters.mgmSignatureSpec.signatureName)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("signed group parameters [$hash]")
    }

    private fun Int.logResult(entity: String): Int {
        if (this == 0) {
            logger.debug {
                "UTXO ledger entity not persisted due to existing row in database. Entity: $entity"
            }
        }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
