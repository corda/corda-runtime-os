package net.corda.ledger.libs.persistence.utxo.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.db.core.utils.BatchPersistenceService
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.libs.persistence.common.mapToComponentGroups
import net.corda.ledger.libs.persistence.util.NamedParamQuery
import net.corda.ledger.libs.persistence.util.NamedParamStatement
import net.corda.ledger.libs.persistence.utxo.SignatureSpec
import net.corda.ledger.libs.persistence.utxo.SignatureWithKey
import net.corda.ledger.libs.persistence.utxo.SignedGroupParameters
import net.corda.ledger.libs.persistence.utxo.UtxoRepository
import net.corda.ledger.utxo.data.transaction.MerkleProofDto
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionDto
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import org.hibernate.Session
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple


@Suppress("TooManyFunctions")
class UtxoRepositoryImpl(
    private val batchPersistenceService: BatchPersistenceService,
    private val serializationService: SerializationService,
    private val wireTransactionFactory: WireTransactionFactory,
    private val queryProvider: UtxoQueryProvider,
) : UtxoRepository {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val TOP_LEVEL_MERKLE_PROOF_INDEX = -1
        private val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    private val findSignedTransactionIdsAndStatusesSql =
        NamedParamQuery.from(queryProvider.findSignedTransactionIdsAndStatuses)

    override fun findTransaction(
        entityManager: EntityManager,
        id: String,
    ): SignedTransactionContainer? {
        val (privacySalt, metadataBytes) = findTransactionsPrivacySaltAndMetadata(entityManager, listOf(id))[id]
            ?: return null
        val wireTransaction = wireTransactionFactory.create(
            mapOf(0 to listOf(metadataBytes)) + findTransactionComponentLeafs(entityManager, id),
            privacySalt
        )
        return SignedTransactionContainer(
            wireTransaction,
            findTransactionSignatures(entityManager, id)
        )
    }

    override fun findSignedTransactionIdsAndStatuses(
        connection: Connection,
        transactionIds: List<String>,
    ): Map<SecureHash, String> {
        val result = mutableMapOf<SecureHash, String>()
         NamedParamStatement(findSignedTransactionIdsAndStatusesSql, connection).use { stmt ->
             stmt.setStrings("transactionIds", transactionIds)
             stmt.executeQuery().use { rs ->
                 while(rs.next()) {
                     result[parseSecureHash(rs.getString(1))] = rs.getString(2)
                 }
             }
         }
        return result
    }

    private fun findTransactionsPrivacySaltAndMetadata(
        entityManager: EntityManager,
        transactionIds: List<String>,
    ): Map<String, Pair<PrivacySaltImpl, ByteArray>?> {
        return entityManager.createNativeQuery(queryProvider.findTransactionsPrivacySaltAndMetadata, Tuple::class.java)
            .setParameter("transactionIds", transactionIds)
            .resultListAsTuples().associate { r ->
                r.get(0) as String to Pair(PrivacySaltImpl(r.get(1) as ByteArray), r.get(2) as ByteArray)
            }
    }

    override fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String,
    ): Map<Int, List<ByteArray>> {
        return entityManager.createNativeQuery(queryProvider.findTransactionComponentLeafs, Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .mapToComponentGroups(UtxoComponentGroupMapper(transactionId))
    }

    private fun findUnconsumedVisibleStates(
        entityManager: EntityManager,
        query: String,
        stateClassType: String?,
    ): List<UtxoVisibleTransactionOutputDto> {
        val queryObj = entityManager.createNativeQuery(query, Tuple::class.java)
            .setParameter("verified", TransactionStatus.VERIFIED.value)

        if (stateClassType != null) {
            queryObj.setParameter("type", stateClassType)
        }

        return queryObj.mapToUtxoVisibleTransactionOutputDto()
    }

    override fun findUnconsumedVisibleStatesByType(
        entityManager: EntityManager,
    ): List<UtxoVisibleTransactionOutputDto> {
        return findUnconsumedVisibleStates(entityManager, queryProvider.findUnconsumedVisibleStatesByType, null)
    }

    override fun resolveStateRefs(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
    ): List<UtxoVisibleTransactionOutputDto> {
        return entityManager.createNativeQuery(queryProvider.resolveStateRefs, Tuple::class.java)
            .setParameter("transactionIds", stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map { it.toString() })
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .mapToUtxoVisibleTransactionOutputDto()
    }

    override fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String,
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createNativeQuery(queryProvider.findTransactionSignatures, Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> serializationService.deserialize(r.get(0) as ByteArray) }
    }

    override fun findSignedTransactionStatus(entityManager: EntityManager, id: String): String? {
        return entityManager.createNativeQuery(queryProvider.findSignedTransactionStatus, Tuple::class.java)
            .setParameter("transactionId", id)
            .resultListAsTuples()
            .map { r -> r.get(0) as String }
            .singleOrNull()
    }

    override fun markTransactionVisibleStatesConsumed(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        timestamp: Instant,
    ) {
        entityManager.createNativeQuery(queryProvider.markTransactionVisibleStatesConsumed)
            .setParameter("consumed", timestamp)
            .setParameter("transactionIds", stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map(StateRef::toString))
            .executeUpdate()
    }

    override fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        status: TransactionStatus,
        metadataHash: String,
    ): Boolean {
        return entityManager.createNativeQuery(queryProvider.persistTransaction)
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .setParameter("status", status.value)
            .setParameter("updatedAt", timestamp)
            .setParameter("metadataHash", metadataHash)
            .executeUpdate() != 0
    }

    override fun persistUnverifiedTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        metadataHash: String,
    ) {
        entityManager.createNativeQuery(queryProvider.persistUnverifiedTransaction)
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .setParameter("updatedAt", timestamp)
            .setParameter("metadataHash", metadataHash)
            .executeUpdate()
            .logResult("transaction [$id]")
    }

    override fun persistFilteredTransactions(
        entityManager: EntityManager,
        filteredTransactions: List<UtxoRepository.FilteredTransaction>,
        timestamp: Instant,
    ) {
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistFilteredTransaction,
                filteredTransactions
            ) { statement, parameterIndex, filteredTransaction ->
                statement.setString(parameterIndex.next(), filteredTransaction.transactionId)
                statement.setBytes(parameterIndex.next(), filteredTransaction.privacySalt)
                statement.setString(parameterIndex.next(), filteredTransaction.account)
                statement.setTimestamp(parameterIndex.next(), Timestamp.from(timestamp), utcCalendar)
                statement.setTimestamp(parameterIndex.next(), Timestamp.from(timestamp), utcCalendar)
                statement.setString(parameterIndex.next(), filteredTransaction.metadataHash)
            }
        }
    }

    override fun updateTransactionToVerified(entityManager: EntityManager, id: String, timestamp: Instant) {
        entityManager.createNativeQuery(queryProvider.updateTransactionToVerified)
            .setParameter("transactionId", id)
            .setParameter("updatedAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionMetadata(
        entityManager: EntityManager,
        hash: String,
        metadataBytes: ByteArray,
        groupParametersHash: String,
        cpiFileChecksum: String,
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionMetadata)
            .setParameter("hash", hash)
            .setParameter("canonicalData", metadataBytes)
            .setParameter("groupParametersHash", groupParametersHash)
            .setParameter("cpiFileChecksum", cpiFileChecksum)
            .executeUpdate()
            .logResult("transaction metadata [$hash]")
    }

    override fun persistTransactionSources(
        entityManager: EntityManager,
        transactionId: String,
        transactionSources: List<UtxoRepository.TransactionSource>,
    ) {
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistTransactionSources,
                transactionSources
            ) { statement, parameterIndex, transactionSource ->
                statement.setString(parameterIndex.next(), transactionId) // new transaction id
                statement.setInt(parameterIndex.next(), transactionSource.group.ordinal) // refs or inputs
                statement.setInt(parameterIndex.next(), transactionSource.index) // index in refs or inputs
                statement.setString(parameterIndex.next(), transactionSource.sourceTransactionId) // tx state came from
                statement.setInt(parameterIndex.next(), transactionSource.sourceIndex) // index from tx it came from
            }
        }
    }

    override fun persistTransactionComponents(
        entityManager: EntityManager,
        transactionId: String,
        components: List<List<ByteArray>>,
        hash: (ByteArray) -> String,
    ) {
        fun isMetadata(groupIndex: Int, leafIndex: Int) = groupIndex == 0 && leafIndex == 0

        val flattenedComponentList = components.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexedNotNull { leafIndex, data ->
                if (isMetadata(groupIndex, leafIndex)) {
                    null
                } else {
                    Triple(groupIndex, leafIndex, data)
                }
            }
        }.flatten()
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistTransactionComponents,
                flattenedComponentList
            ) { statement, parameterIndex, component ->
                statement.setString(parameterIndex.next(), transactionId)
                statement.setInt(parameterIndex.next(), component.first)
                statement.setInt(parameterIndex.next(), component.second)
                statement.setBytes(parameterIndex.next(), component.third)
                statement.setString(parameterIndex.next(), hash(component.third))
            }
        }
    }

    override fun persistTransactionComponents(
        entityManager: EntityManager,
        components: List<UtxoRepository.TransactionComponent>,
        hash: (ByteArray) -> String,
    ) {
        fun isMetadata(groupIndex: Int, leafIndex: Int) = groupIndex == 0 && leafIndex == 0

        val componentsWithMetadataRemoved = components.mapNotNull { component ->
            val (_, groupIndex, leafIndex) = component
            if (isMetadata(groupIndex, leafIndex)) {
                null
            } else {
                component
            }
        }
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistTransactionComponents,
                componentsWithMetadataRemoved
            ) { statement, parameterIndex, component ->
                statement.setString(parameterIndex.next(), component.transactionId)
                statement.setInt(parameterIndex.next(), component.groupIndex)
                statement.setInt(parameterIndex.next(), component.leafIndex)
                statement.setBytes(parameterIndex.next(), component.leafData)
                statement.setString(parameterIndex.next(), hash(component.leafData))
            }
        }
    }

    override fun persistVisibleTransactionOutputs(
        entityManager: EntityManager,
        transactionId: String,
        timestamp: Instant,
        visibleTransactionOutputs: List<UtxoRepository.VisibleTransactionOutput>,
    ) {
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistVisibleTransactionOutputs,
                visibleTransactionOutputs
            ) { statement, parameterIndex, visibleTransactionOutput ->
                statement.setString(parameterIndex.next(), transactionId)
                statement.setInt(parameterIndex.next(), UtxoComponentGroup.OUTPUTS.ordinal)
                statement.setInt(parameterIndex.next(), visibleTransactionOutput.stateIndex)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.className)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.token?.poolKey?.tokenType)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.token?.poolKey?.issuerHash?.toString())
                statement.setString(parameterIndex.next(), visibleTransactionOutput.notaryName)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.token?.poolKey?.symbol)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.token?.filterFields?.tag)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.token?.filterFields?.ownerHash?.toString())
                if (visibleTransactionOutput.token != null) {
                    statement.setBigDecimal(parameterIndex.next(), visibleTransactionOutput.token.amount)
                } else {
                    statement.setNull(parameterIndex.next(), Types.NUMERIC)
                }

                if (visibleTransactionOutput.token?.priority != null) {
                    statement.setLong(parameterIndex.next(), visibleTransactionOutput.token.priority!!)
                } else {
                    statement.setNull(parameterIndex.next(), Types.BIGINT)
                }

                statement.setTimestamp(parameterIndex.next(), Timestamp.from(timestamp), utcCalendar)
                statement.setNull(parameterIndex.next(), Types.TIMESTAMP)
                statement.setString(parameterIndex.next(), visibleTransactionOutput.customRepresentation.json)
            }
        }
    }

    override fun persistTransactionSignatures(
        entityManager: EntityManager,
        signatures: List<UtxoRepository.TransactionSignature>,
        timestamp: Instant,
    ) {
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistTransactionSignatures,
                signatures
            ) { statement, parameterIndex, signature ->
                statement.setString(parameterIndex.next(), signature.transactionId)
                statement.setString(parameterIndex.next(), signature.publicKeyHash.toString())
                statement.setBytes(parameterIndex.next(), signature.signatureBytes)
                statement.setTimestamp(parameterIndex.next(), Timestamp.from(timestamp), utcCalendar)
            }
        }
    }

    override fun updateTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant,
    ) {
        // Update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
        val rowsUpdated = entityManager.createNativeQuery(queryProvider.updateTransactionStatus)
            .setParameter("transactionId", transactionId)
            .setParameter("newStatus", transactionStatus.value)
            .setParameter("updatedAt", timestamp)
            .executeUpdate()

        check(rowsUpdated == 1 || transactionStatus == TransactionStatus.UNVERIFIED) {
            // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
            "Existing status for transaction with ID $transactionId can't be updated to $transactionStatus"
        }
    }

    override fun findSignedGroupParameters(entityManager: EntityManager, hash: String): SignedGroupParameters? {
        return entityManager.createNativeQuery(queryProvider.findSignedGroupParameters, Tuple::class.java)
            .setParameter("hash", hash)
            .resultListAsTuples()
            .map { r ->
                SignedGroupParameters(
                    r.get(0) as ByteArray,
                    SignatureWithKey(
                        r.get(1) as ByteArray,
                        r.get(2) as ByteArray
                    ),
                    SignatureSpec((r.get(3) as String), null, null)
                )
            }
            .singleOrNull()
    }

    override fun persistSignedGroupParameters(
        entityManager: EntityManager,
        hash: String,
        signedGroupParameters: SignedGroupParameters,
        timestamp: Instant,
    ) {
        entityManager.createNativeQuery(queryProvider.persistSignedGroupParameters)
            .setParameter("hash", hash)
            .setParameter("parameters", signedGroupParameters.groupParameters)
            .setParameter("signature_public_key", signedGroupParameters.mgmSignature.publicKey)
            .setParameter("signature_content", signedGroupParameters.mgmSignature.bytes)
            .setParameter("signature_spec", signedGroupParameters.mgmSignatureSpec.signatureName)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("signed group parameters [$hash]")
    }

    override fun persistMerkleProofs(entityManager: EntityManager, merkleProofs: List<UtxoRepository.TransactionMerkleProof>) {
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistMerkleProofs,
                merkleProofs
            ) { statement, parameterIndex, merkleProof ->
                statement.setString(parameterIndex.next(), merkleProof.merkleProofId)
                statement.setString(parameterIndex.next(), merkleProof.transactionId)
                statement.setInt(parameterIndex.next(), merkleProof.groupIndex)
                statement.setInt(parameterIndex.next(), merkleProof.treeSize)
                statement.setString(parameterIndex.next(), merkleProof.leafIndexes.joinToString(","))
                statement.setString(parameterIndex.next(), merkleProof.leafHashes.joinToString(","))
            }
        }
    }

    override fun persistMerkleProofLeaves(entityManager: EntityManager, leaves: List<UtxoRepository.TransactionMerkleProofLeaf>) {
        entityManager.connection { connection ->
            batchPersistenceService.persistBatch(
                connection,
                queryProvider.persistMerkleProofLeaves,
                leaves
            ) { statement, parameterIndex, leaf ->
                statement.setString(parameterIndex.next(), leaf.merkleProofId)
                statement.setInt(parameterIndex.next(), leaf.leafIndex)
            }
        }
    }

    override fun findMerkleProofs(
        entityManager: EntityManager,
        transactionIds: List<String>,
    ): Map<String, List<MerkleProofDto>> {
        return entityManager.createNativeQuery(queryProvider.findMerkleProofs, Tuple::class.java)
            .setParameter("transactionIds", transactionIds)
            .resultListAsTuples()
            .groupBy { tuple ->
                // We'll have multiple rows for the same Merkle proof if it revealed more than one leaf
                // We group the rows by the Merkle proof ID to see which are the ones that belong together
                tuple.get(0) as String // Merkle Proof ID
            }.map { (_, rows) ->
                // We can retrieve most of the properties from the first row because they will be the same for each row
                val firstRow = rows.first()

                MerkleProofDto(
                    firstRow.get(1) as String, // Transaction ID
                    firstRow.get(2) as Int, // Group index
                    firstRow.get(3) as Int, // Tree size

                    // We store the hashes as a comma separated string, so we need to split it and parse into SecureHash
                    // we filter out the blank ones just in case
                    (firstRow.get(5) as String).split(",")
                        .filter { it.isNotBlank() }.map { parseSecureHash(it) },

                    firstRow.get(6) as ByteArray, // Privacy salt

                    // Each leaf will have its own row, so we need to go through each row that belongs to the Merkle proof
                    rows.mapNotNull {
                        // Map the leaf index to the data we fetched from the component table
                        val leafIndex = it.get(7) as? Int
                        val leafData = it.get(8) as? ByteArray

                        if (leafIndex != null && leafData != null) {
                            leafIndex to leafData
                        } else {
                            null
                        }
                    }.toMap(),
                    // We store the leaf indexes as a comma separated string, so we need to split it
                    (firstRow.get(4) as String).split(",").map { it.toInt() },

                )
            }.groupBy {
                // Group by transaction ID
                it.transactionId
            }
    }

    override fun findFilteredTransactions(
        entityManager: EntityManager,
        ids: List<String>,
    ): Map<String, UtxoFilteredTransactionDto> {
        val privacySaltAndMetadataMap = findTransactionsPrivacySaltAndMetadata(entityManager, ids)
        val merkleProofs = findMerkleProofs(entityManager, ids)
        val signaturesMap = ids.associateWith { findTransactionSignatures(entityManager, it) }

        return ids.associateWith { transactionId ->

            val transactionMerkleProofs = merkleProofs[transactionId] ?: emptyList()

            val (topLevelMerkleProofs, componentGroupMerkleProofs) = if (transactionMerkleProofs.isNotEmpty()) {
                val (topLevel, componentGroupLevel) = transactionMerkleProofs.partition {
                    it.groupIndex == TOP_LEVEL_MERKLE_PROOF_INDEX
                }
                topLevel to componentGroupLevel
            } else {
                emptyList<MerkleProofDto>() to emptyList()
            }

            val transactionPrivacySaltAndMetadata = privacySaltAndMetadataMap[transactionId]
            val transactionSignatures = signaturesMap[transactionId] ?: emptyList()

            val componentGroupMerkleProofMap = if (componentGroupMerkleProofs.isNotEmpty()) {
                componentGroupMerkleProofs.groupBy { it.groupIndex }
            } else {
                emptyMap()
            }

            UtxoFilteredTransactionDto(
                transactionId = transactionId,
                topLevelMerkleProofs = topLevelMerkleProofs,
                componentMerkleProofMap = componentGroupMerkleProofMap,
                privacySalt = transactionPrivacySaltAndMetadata?.first,
                metadataBytes = transactionPrivacySaltAndMetadata?.second,
                signatures = transactionSignatures
            )
        }
    }

    // select from transaction sources where input states of "previous" transaction are seen as sourceTransactionIds + indexes
    override fun findConsumedTransactionSourcesForTransaction(
        entityManager: EntityManager,
        transactionId: String,
        indexes: List<Int>,
    ): List<Int> {
        return entityManager.createNativeQuery(queryProvider.findConsumedTransactionSourcesForTransaction)
            .setParameter("transactionId", transactionId)
            .setParameter("inputStateIndexes", indexes)
            .resultList
            .map { it as Int }
    }

    override fun findTransactionsWithStatusCreatedBetweenTime(
        entityManager: EntityManager,
        status: TransactionStatus,
        from: Instant,
        until: Instant,
        limit: Int,
    ): List<String> {
        @Suppress("UNCHECKED_CAST")
        return entityManager.createNativeQuery(queryProvider.findTransactionsWithStatusCreatedBetweenTime)
            .setParameter("status", status.value)
            .setParameter("from", from)
            .setParameter("until", until)
            .setMaxResults(limit)
            .resultList as List<String>
    }

    override fun incrementTransactionRepairAttemptCount(
        entityManager: EntityManager,
        id: String,
    ) {
        entityManager.createNativeQuery(queryProvider.incrementRepairAttemptCount)
            .setParameter("transactionId", id)
            .executeUpdate()
    }

    override fun stateRefsExist(entityManager: EntityManager, stateRefs: List<StateRef>): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        if (stateRefs.isNotEmpty()) {
            entityManager.connection { connection ->
                connection.prepareStatement(queryProvider.stateRefsExist(stateRefs.size)).use { statement ->
                    val parameterIndex = generateSequence(1) { it + 1 }.iterator()
                    for (stateRef in stateRefs) {
                        statement.setString(parameterIndex.next(), stateRef.transactionId.toString())
                        statement.setInt(parameterIndex.next(), stateRef.index)
                    }
                    val resultSet = statement.executeQuery()
                    while (resultSet.next()) {
                        results += resultSet.getString(1) to resultSet.getInt(2)
                    }
                }
            }
        }
        return results
    }

    private fun <T> EntityManager.connection(block: (connection: Connection) -> T) {
        val hibernateSession = unwrap(Session::class.java)
        hibernateSession.doWork { connection ->
            block(connection)
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

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>

    private fun Query.mapToUtxoVisibleTransactionOutputDto(): List<UtxoVisibleTransactionOutputDto> {
        return resultListAsTuples()
            .map { t ->
                UtxoVisibleTransactionOutputDto(
                    t[0] as String, // transactionId
                    t[1] as Int, // leaf ID
                    t[2] as ByteArray, // outputs info data
                    t[3] as ByteArray // outputs data
                )
            }
    }
}
