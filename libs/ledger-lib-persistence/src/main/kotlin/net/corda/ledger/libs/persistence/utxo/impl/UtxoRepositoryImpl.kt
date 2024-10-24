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
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone
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
    private val findTransactionsPrivacySaltAndMetadataSql =
        NamedParamQuery.from(queryProvider.findTransactionsPrivacySaltAndMetadata)
    private val findTransactionComponentLeafsSql =
        NamedParamQuery.from(queryProvider.findTransactionComponentLeafs)
    private val findTransactionSignaturesSql =
        NamedParamQuery.from(queryProvider.findTransactionSignatures)
    private val findSignedTransactionStatusSql =
        NamedParamQuery.from(queryProvider.findSignedTransactionStatus)
    private val findMerkleProofsSql =
        NamedParamQuery.from(queryProvider.findMerkleProofs)
    private val findUnconsumedVisibleStatesByTypeSql =
        NamedParamQuery.from(queryProvider.findUnconsumedVisibleStatesByType)
    private val resolveStateRefsSql =
        NamedParamQuery.from(queryProvider.resolveStateRefs)
    private val markTransactionVisibleStatesConsumedSql =
        NamedParamQuery.from(queryProvider.markTransactionVisibleStatesConsumed)
    private val persistTransactionMetadataSql =
        NamedParamQuery.from(queryProvider.persistTransactionMetadata)
    private val persistTransactionSql =
        NamedParamQuery.from(queryProvider.persistTransaction)
    private val persistUnverifiedTransactionSql =
        NamedParamQuery.from(queryProvider.persistUnverifiedTransaction)
    private val findConsumedTransactionSourcesForTransactionSql =
        NamedParamQuery.from(queryProvider.findConsumedTransactionSourcesForTransaction)
    private val updateTransactionToVerifiedSql =
        NamedParamQuery.from(queryProvider.updateTransactionToVerified)
    private val updateTransactionStatusSql =
        NamedParamQuery.from(queryProvider.updateTransactionStatus)
    private val persistSignedGroupParametersSql =
        NamedParamQuery.from(queryProvider.persistSignedGroupParameters)
    private val findSignedGroupParametersSql =
        NamedParamQuery.from(queryProvider.findSignedGroupParameters)
    private val findTransactionsWithStatusCreatedBetweenTimeSql =
        NamedParamQuery.from(queryProvider.findTransactionsWithStatusCreatedBetweenTime)
    private val incrementRepairAttemptCountSql =
        NamedParamQuery.from(queryProvider.incrementRepairAttemptCount)

    override fun findTransaction(
        connection: Connection,
        id: String,
    ): SignedTransactionContainer? {
        val (privacySalt, metadataBytes) = findTransactionsPrivacySaltAndMetadata(connection, listOf(id))[id]
            ?: return null
        val wireTransaction = wireTransactionFactory.create(
            mapOf(0 to listOf(metadataBytes)) + findTransactionComponentLeafs(connection, id),
            privacySalt
        )
        return SignedTransactionContainer(
            wireTransaction,
            findTransactionSignatures(connection, id)
        )
    }

    override fun findSignedTransactionIdsAndStatuses(
        connection: Connection,
        transactionIds: List<String>,
    ): Map<SecureHash, String> {
        NamedParamStatement(findSignedTransactionIdsAndStatusesSql, connection).use { stmt ->
            stmt.setStrings("transactionIds", transactionIds)
            return stmt.executeQueryAsList {
                parseSecureHash(it.getString(1)) to it.getString(2)
            }.toMap()
        }
    }

    private fun findTransactionsPrivacySaltAndMetadata(
        connection: Connection,
        transactionIds: List<String>,
    ): Map<String, Pair<PrivacySaltImpl, ByteArray>?> {
        NamedParamStatement(findTransactionsPrivacySaltAndMetadataSql, connection).use { stmt ->
            stmt.setStrings("transactionIds", transactionIds)
            return stmt.executeQueryAsList {
                it.getString(1) to Pair(PrivacySaltImpl(it.getBytes(2)), it.getBytes(3))
            }.toMap()
        }
    }

    override fun findTransactionComponentLeafs(
        connection: Connection,
        transactionId: String,
    ): Map<Int, List<ByteArray>> {
        NamedParamStatement(findTransactionComponentLeafsSql, connection).use { stmt ->
            stmt.setString("transactionId", transactionId)
            return stmt.executeQueryAsListOfColumns()
                .mapToComponentGroups(UtxoComponentGroupMapper(transactionId))
        }
    }

    override fun findUnconsumedVisibleStatesByType(
        connection: Connection,
    ): List<UtxoVisibleTransactionOutputDto> {
        NamedParamStatement(findUnconsumedVisibleStatesByTypeSql, connection).use { stmt ->
            stmt.setString("verified", TransactionStatus.VERIFIED.value)
            return stmt.executeQueryAsList {
                UtxoVisibleTransactionOutputDto(
                    it.getString(1), // transactionId
                    it.getInt(2), // leaf ID
                    it.getBytes(3), // outputs info data
                    it.getBytes(4) // outputs data
                )
            }
        }
    }

    override fun resolveStateRefs(
        connection: Connection,
        stateRefs: List<StateRef>,
    ): List<UtxoVisibleTransactionOutputDto> {
        NamedParamStatement(resolveStateRefsSql, connection).use { stmt ->
            stmt.setStrings("transactionIds", stateRefs.map { it.transactionId.toString() })
            stmt.setStrings("stateRefs", stateRefs.map { it.toString() })
            stmt.setString("verified", TransactionStatus.VERIFIED.value)
            return stmt.executeQueryAsList {
                UtxoVisibleTransactionOutputDto(
                    it.getString(1), // transactionId
                    it.getInt(2), // leaf ID
                    it.getBytes(3), // outputs info data
                    it.getBytes(4) // outputs data
                )
            }
        }
    }

    override fun findTransactionSignatures(
        connection: Connection,
        transactionId: String,
    ): List<DigitalSignatureAndMetadata> {
        NamedParamStatement(findTransactionSignaturesSql, connection).use { stmt ->
            stmt.setString("transactionId", transactionId)
            return stmt.executeQueryAsList {
                serializationService.deserialize(it.getBytes(1))
            }
        }
    }

    override fun findSignedTransactionStatus(connection: Connection, id: String): String? {
        NamedParamStatement(findSignedTransactionStatusSql, connection).use { stmt ->
            stmt.setString("transactionId", id)
            return stmt.executeQueryAsList { it.getString(1) }.singleOrNull()
        }
    }

    override fun markTransactionVisibleStatesConsumed(
        connection: Connection,
        stateRefs: List<StateRef>,
        timestamp: Instant,
    ) {
        NamedParamStatement(markTransactionVisibleStatesConsumedSql, connection).use { stmt ->
            stmt.setInstant("consumed", timestamp)
            stmt.setStrings("transactionIds", stateRefs.map { it.transactionId.toString() })
            stmt.setStrings("stateRefs", stateRefs.map(StateRef::toString))

            stmt.executeUpdate()
        }
    }

    override fun persistTransaction(
        connection: Connection,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        status: TransactionStatus,
        metadataHash: String,
    ): Boolean {
        NamedParamStatement(persistTransactionSql, connection).use { stmt ->
            stmt.setString("id", id)
            stmt.setBytes("privacySalt", privacySalt)
            stmt.setString("accountId", account)
            stmt.setInstant("createdAt", timestamp)
            stmt.setString("status", status.value)
            stmt.setInstant("updatedAt", timestamp)
            stmt.setString("metadataHash", metadataHash)

            return stmt.executeUpdate() != 0
        }
    }

    override fun persistUnverifiedTransaction(
        connection: Connection,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        metadataHash: String,
    ) {
        NamedParamStatement(persistUnverifiedTransactionSql, connection).use { stmt ->
            stmt.setString("id", id)
            stmt.setBytes("privacySalt", privacySalt)
            stmt.setString("accountId", account)
            stmt.setInstant("createdAt", timestamp)
            stmt.setInstant("updatedAt", timestamp)
            stmt.setString("metadataHash", metadataHash)

            stmt.executeUpdate().logResult("transaction [$id]")
        }
    }

    override fun persistFilteredTransactions(
        connection: Connection,
        filteredTransactions: List<UtxoRepository.FilteredTransaction>,
        timestamp: Instant,
    ) {
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

    override fun updateTransactionToVerified(connection: Connection, id: String, timestamp: Instant) {
        NamedParamStatement(updateTransactionToVerifiedSql, connection).use { stmt ->
            stmt.setString("transactionId", id)
            stmt.setInstant("updatedAt", timestamp)

            stmt.executeUpdate()
        }
    }

    override fun persistTransactionMetadata(
        connection: Connection,
        hash: String,
        metadataBytes: ByteArray,
        groupParametersHash: String,
        cpiFileChecksum: String,
    ) {
        NamedParamStatement(persistTransactionMetadataSql, connection).use { stmt ->
            stmt.setString("hash", hash)
            stmt.setBytes("canonicalData", metadataBytes)
            stmt.setString("groupParametersHash", groupParametersHash)
            stmt.setString("cpiFileChecksum", cpiFileChecksum)
            stmt.executeUpdate().logResult("transaction metadata [$hash]")
        }
    }

    override fun persistTransactionSources(
        connection: Connection,
        transactionId: String,
        transactionSources: List<UtxoRepository.TransactionSource>,
    ) {
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

    override fun persistTransactionComponents(
        connection: Connection,
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

    override fun persistTransactionComponents(
        connection: Connection,
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

    override fun persistVisibleTransactionOutputs(
        connection: Connection,
        transactionId: String,
        timestamp: Instant,
        visibleTransactionOutputs: List<UtxoRepository.VisibleTransactionOutput>,
    ) {
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

    override fun persistTransactionSignatures(
        connection: Connection,
        signatures: List<UtxoRepository.TransactionSignature>,
        timestamp: Instant,
    ) {
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

    override fun updateTransactionStatus(
        connection: Connection,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant,
    ) {
        NamedParamStatement(updateTransactionStatusSql, connection).use { stmt ->
            stmt.setString("transactionId", transactionId)
            stmt.setString("newStatus", transactionStatus.value)
            stmt.setString("existingStatus", transactionStatus.value)
            stmt.setInstant("updatedAt", timestamp)

            // Update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
            val rowsUpdated = stmt.executeUpdate()
            check(rowsUpdated == 1 || transactionStatus == TransactionStatus.UNVERIFIED) {
                // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
                "Existing status for transaction with ID $transactionId can't be updated to $transactionStatus"
            }
        }
    }

    override fun findSignedGroupParameters(connection: Connection, hash: String): SignedGroupParameters? {
        NamedParamStatement(findSignedGroupParametersSql, connection).use { stmt ->
            stmt.setString("hash", hash)

            return stmt
                .executeQueryAsList { r ->
                    SignedGroupParameters(
                        r.getBytes(1),
                        SignatureWithKey(
                            r.getBytes(2),
                            r.getBytes(3)
                        ),
                        SignatureSpec(r.getString(4), null, null)
                    )
                }
                .singleOrNull()
        }
    }

    override fun persistSignedGroupParameters(
        connection: Connection,
        hash: String,
        signedGroupParameters: SignedGroupParameters,
        timestamp: Instant,
    ) {
        NamedParamStatement(persistSignedGroupParametersSql, connection).use { stmt ->
            stmt.setString("hash", hash)
            stmt.setBytes("parameters", signedGroupParameters.groupParameters)
            stmt.setBytes("signature_public_key", signedGroupParameters.mgmSignature.publicKey)
            stmt.setBytes("signature_content", signedGroupParameters.mgmSignature.bytes)
            stmt.setString("signature_spec", signedGroupParameters.mgmSignatureSpec.signatureName)
            stmt.setInstant("createdAt", timestamp)
            stmt.executeUpdate().logResult("signed group parameters [$hash]")
        }
    }

    override fun persistMerkleProofs(connection: Connection, merkleProofs: List<UtxoRepository.TransactionMerkleProof>) {
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

    override fun persistMerkleProofLeaves(connection: Connection, leaves: List<UtxoRepository.TransactionMerkleProofLeaf>) {
        batchPersistenceService.persistBatch(
            connection,
            queryProvider.persistMerkleProofLeaves,
            leaves
        ) { statement, parameterIndex, leaf ->
            statement.setString(parameterIndex.next(), leaf.merkleProofId)
            statement.setInt(parameterIndex.next(), leaf.leafIndex)
        }
    }

    override fun findMerkleProofs(
        connection: Connection,
        transactionIds: List<String>,
    ): Map<String, List<MerkleProofDto>> {
        NamedParamStatement(findMerkleProofsSql, connection).use { stmt ->
            stmt.setStrings("transactionIds", transactionIds)
            return stmt.executeQueryAsListOfColumns()
                .groupBy { tuple ->
                    // We'll have multiple rows for the same Merkle proof if it revealed more than one leaf
                    // We group the rows by the Merkle proof ID to see which are the ones that belong together
                    tuple[0] as String // Merkle Proof ID
                }.map { (_, rows) ->
                    // We can retrieve most of the properties from the first row because they will be the same for each row
                    val firstRow = rows.first()

                    MerkleProofDto(
                        firstRow[1] as String, // Transaction ID
                        firstRow[2] as Int, // Group index
                        firstRow[3] as Int, // Tree size

                        // We store the hashes as a comma separated string, so we need to split it and parse into SecureHash
                        // we filter out the blank ones just in case
                        (firstRow[5] as String).split(",")
                            .filter { it.isNotBlank() }.map { parseSecureHash(it) },

                        firstRow[6] as ByteArray, // Privacy salt

                        // Each leaf will have its own row, so we need to go through each row that belongs to the Merkle proof
                        rows.mapNotNull {
                            // Map the leaf index to the data we fetched from the component table
                            val leafIndex = it[7] as? Int
                            val leafData = it[8] as? ByteArray

                            if (leafIndex != null && leafData != null) {
                                leafIndex to leafData
                            } else {
                                null
                            }
                        }.toMap(),
                        // We store the leaf indexes as a comma separated string, so we need to split it
                        (firstRow[4] as String).split(",").map { it.toInt() },

                    )
                }.groupBy {
                    // Group by transaction ID
                    it.transactionId
                }
        }
    }

    override fun findFilteredTransactions(
        connection: Connection,
        ids: List<String>,
    ): Map<String, UtxoFilteredTransactionDto> {
        val privacySaltAndMetadataMap = findTransactionsPrivacySaltAndMetadata(connection, ids)
        val merkleProofs = findMerkleProofs(connection, ids)
        val signaturesMap = ids.associateWith { findTransactionSignatures(connection, it) }

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
        connection: Connection,
        transactionId: String,
        indexes: List<Int>,
    ): List<Int> {
        NamedParamStatement(findConsumedTransactionSourcesForTransactionSql, connection).use { stmt ->
            stmt.setString("transactionId", transactionId)
            stmt.setInts("inputStateIndexes", indexes)

            return stmt.executeQueryAsList {
                it.getInt(1)
            }
        }
    }

    override fun findTransactionsWithStatusCreatedBetweenTime(
        connection: Connection,
        status: TransactionStatus,
        from: Instant,
        until: Instant,
        limit: Int,
    ): List<String> {
        NamedParamStatement(findTransactionsWithStatusCreatedBetweenTimeSql, connection).use { stmt ->
            stmt.setString("status", status.value)
            stmt.setInstant("from", from)
            stmt.setInstant("until", until)
            stmt.setInt("limit", limit)

            return stmt.executeQueryAsList {
                it.getString(1)
            }
        }
    }

    override fun incrementTransactionRepairAttemptCount(
        connection: Connection,
        id: String,
    ) {
        NamedParamStatement(incrementRepairAttemptCountSql, connection).use { stmt ->
            stmt.setString("transactionId", id)
            stmt.executeUpdate()
        }
    }

    override fun stateRefsExist(connection: Connection, stateRefs: List<StateRef>): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        if (stateRefs.isNotEmpty()) {
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
        return results
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
