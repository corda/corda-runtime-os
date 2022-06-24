package net.corda.chunking.db.impl.persistence

import net.corda.chunking.Checksum
import net.corda.chunking.RequestId
import net.corda.chunking.datamodel.ChunkEntity
import net.corda.chunking.datamodel.ChunkPropertyEntity
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.chunking.toAvro
import net.corda.chunking.toCorda
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.chunking.Chunk
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkFileEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.orm.utils.transaction
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.LockModeType
import javax.persistence.NonUniqueResultException
import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey
import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.packaging.core.CpkIdentifier

/**
 * This class provides some simple APIs to interact with the database.
 */
@Suppress("TooManyFunctions")
class DatabaseChunkPersistence(private val entityManagerFactory: EntityManagerFactory) : ChunkPersistence {
    private companion object {
        val log = contextLogger()

        private fun Chunk.toDbProperties(): List<ChunkPropertyEntity> {
            return properties?.items?.map {
                ChunkPropertyEntity(requestId, it.key, it.value, Instant.now())
            } ?: emptyList()
        }

        private fun EntityManager.getAvroChunkPropertiesInTransaction(requestId: RequestId): KeyValuePairList? {

            val chunkProperties = createQuery(
                """
                SELECT c FROM ${ChunkPropertyEntity::class.simpleName} c
                WHERE c.requestId = :requestId
                """.trimIndent(),
                ChunkPropertyEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultList

            return if (chunkProperties.isEmpty()) {
                null
            } else {
                KeyValuePairList.newBuilder().setItems(
                    chunkProperties.map { KeyValuePair(it.key, it.value) }.toList()
                ).build()
            }
        }
    }

    /**
     * Persist a chunk to the database and also check whether we have all chunks,
     * and return [AllChunksReceived.YES] or `NO`.
     *
     * This occurs in a transaction so that only one process should "raise" one
     * final message to the rest of the system to validate the complete binary.
     */

    override fun persistChunk(chunk: Chunk): AllChunksReceived {
        // We put `null` into the data field if the array is empty, i.e. the final chunk
        // so that we can write easier queries for the parts expected and received.
        val data = if (chunk.data.array().isEmpty()) null else chunk.data.array()
        val cordaSecureHash = chunk.checksum?.toCorda()
        var status = AllChunksReceived.NO
        entityManagerFactory.createEntityManager().transaction { em ->
            // Persist this chunk.
            val chunkEntity = ChunkEntity(
                chunk.requestId,
                chunk.fileName,
                cordaSecureHash?.toString(),
                chunk.partNumber,
                chunk.offset,
                data
            )

            em.persist(chunkEntity)

            val chunkProperties = chunk.toDbProperties()

            // Merge because same chunk property may already exist
            chunkProperties.forEach {
                em.merge(it)
            }

            // At this point we have at least one chunk.
            val table = ChunkEntity::class.simpleName
            val chunkCountIfComplete = em.createQuery(
                """
                SELECT 'hasAllChunks' FROM $table c1
                INNER JOIN $table c2 ON c1.requestId = c2.requestId AND c2.data IS NULL
                WHERE c1.requestId = :requestId AND c1.data IS NOT NULL
                GROUP BY c1.requestId, c2.partNumber
                HAVING COUNT(c1.requestId) = c2.partNumber
                """.trimIndent()
            )
                .setParameter("requestId", chunk.requestId)
                .resultList.count()

            // [chunkCountIfComplete] is either 0, i.e. incomplete, or some non-zero value
            // which is the total number chunks, if complete.
            if (chunkCountIfComplete != 0) {
                status = AllChunksReceived.YES
            }
        }

        return status
    }

    /**
     * Is the checksum for the given [requestId] valid?
     *
     * Assumes that all chunks have been received for the given [requestId]
     */
    override fun checksumIsValid(requestId: RequestId): Boolean {
        var expectedChecksum: SecureHash? = null
        var actualChecksum: ByteArray? = null

        entityManagerFactory.createEntityManager().transaction { em ->
            val streamingResults = em.createQuery(
                "SELECT c FROM ${ChunkEntity::class.simpleName} c " +
                        "WHERE c.requestId = :requestId " +
                        "ORDER BY c.partNumber ASC",
                ChunkEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultStream

            val messageDigest = Checksum.newMessageDigest()

            streamingResults.use {
                it.forEach { e ->
                    if (e.data == null) { // zero chunk
                        if (e.checksum == null) throw CordaRuntimeException("No checksum found in zero-sized chunk")
                        expectedChecksum = SecureHash.create(e.checksum!!)
                    } else { // non-zero chunk
                        messageDigest.update(e.data!!)
                    }
                }
            }

            if (expectedChecksum == null) throw CordaRuntimeException("Expected checksum not set because no zero-sized chunk was found")

            actualChecksum = messageDigest.digest()
        }

        return expectedChecksum!!.bytes.contentEquals(actualChecksum)
    }

    /**
     * Gets chunks (if any) for a given [requestId] from database and calls [onChunk] for each chunk that is returned.
     *
     * @param requestId the requestId of the chunks
     * @param onChunk lambda method to be called on each chunk
     */
    override fun forEachChunk(requestId: RequestId, onChunk: (chunk: Chunk) -> Unit) {
        entityManagerFactory.createEntityManager().transaction { em ->
            val table = ChunkEntity::class.simpleName
            val streamingResults = em.createQuery(
                """
                SELECT c FROM $table c
                WHERE c.requestId = :requestId
                ORDER BY c.requestId ASC
                """.trimIndent(),
                ChunkEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultStream

            val avroChunkProperties = em.getAvroChunkPropertiesInTransaction(requestId)

            streamingResults.use {
                it.forEach { entity ->
                    // Do the reverse of [persist] particularly for data - if null, return zero bytes.
                    val checksum = if (entity.checksum != null) SecureHash.create(entity.checksum!!).toAvro() else null
                    val data = if (entity.data != null) ByteBuffer.wrap(entity.data) else ByteBuffer.allocate(0)
                    val chunk = Chunk(
                        requestId, entity.fileName, checksum, entity.partNumber, entity.offset, data,
                        avroChunkProperties
                    )
                    onChunk(chunk)
                }
            }
        }
    }

    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in database
     */
    override fun cpkExists(cpkChecksum: SecureHash): Boolean {
        val query = "SELECT count(c) FROM ${CpkFileEntity::class.simpleName} c WHERE c.fileChecksum = :cpkFileChecksum"
        val entitiesFound = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(query)
                .setParameter("cpkFileChecksum", cpkChecksum.toString())
                .singleResult as Long
        }

        if (entitiesFound > 1) throw NonUniqueResultException("CpkFileEntity with fileChecksum = $cpkChecksum was not unique")

        return entitiesFound > 0
    }

    override fun cpiExists(cpiName: String, cpiVersion: String, signerSummaryHash: String): Boolean =
        getCpiMetadataEntity(cpiName, cpiVersion, signerSummaryHash) != null

    override fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity {
        entityManagerFactory.createEntityManager().transaction { em ->
            val cpkMetadataEntities = createOrUpdateCpkMetadataEntities(cpi, emptyMap())

            val cpiMetadataEntity = createCpiMetadataEntity(cpi, cpiFileName, checksum, requestId, groupId, cpkMetadataEntities)
            
            val managedCpiMetadataEntity = em.merge(cpiMetadataEntity)

            val cpkFileEntities = createOrUpdateExistingCpkFileEntities(em, cpi.cpks)
            cpkFileEntities.forEach { em.merge(it) }

            cpkDbChangeLogEntities.forEach { em.merge(it) }

            return@persistMetadataAndCpks managedCpiMetadataEntity
        }
    }

    override fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity {
        val cpiId = cpi.metadata.cpiId
        log.info("Performing updateMetadataAndCpks for: ${cpiId.name} v${cpiId.version}")

        // Perform update of CPI and store CPK along with its metadata
        entityManagerFactory.createEntityManager().transaction { em ->
            // We cannot delete old representation of CPI as there is FK constraint from `vnode_instance`
            val existingMetadataEntity = requireNotNull(
                findCpiMetadataEntityInTransaction(
                    em,
                    cpiId.name,
                    cpiId.version,
                    cpiId.signerSummaryHashForDbQuery
                )
            ) {
                "Cannot find CPI metadata for ${cpiId.name} v${cpiId.version}"
            }

            val cpiCpkEntities = createOrUpdateCpkMetadataEntities(cpi, existingMetadataEntity.cpks.associateBy { it.metadata.id })

            val updatedMetadata = existingMetadataEntity.update(
                fileUploadRequestId = requestId,
                fileName = cpiFileName,
                fileChecksum = checksum.toString(),
                cpks = cpiCpkEntities
            )

            val cpiMetadataEntity = em.merge(updatedMetadata)

            val cpkFileEntities = createOrUpdateExistingCpkFileEntities(em, cpi.cpks)
            cpkFileEntities.forEach { em.merge(it) }
            cpkDbChangeLogEntities.forEach { em.merge(it) }

            return cpiMetadataEntity
        }
    }

    private fun findCpiMetadataEntityInTransaction(
        entityManager: EntityManager,
        name: String,
        version: String,
        signerSummaryHash: String
    ): CpiMetadataEntity? {
        val primaryKey = CpiMetadataEntityKey(
            name,
            version,
            signerSummaryHash
        )

        return entityManager.find(
            CpiMetadataEntity::class.java,
            primaryKey,
            // In case of force update, we want the entity to change regardless of whether the CPI being uploaded
            //  is identical to an existing.
            //  OPTIMISTIC_FORCE_INCREMENT means the version number will always be bumped.
            LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    }

    private fun getCpiMetadataEntity(name: String, version: String, signerSummaryHash: String): CpiMetadataEntity? {
        return entityManagerFactory.createEntityManager().transaction {
            findCpiMetadataEntityInTransaction(it, name, version, signerSummaryHash)
        }
    }

    override fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String? {
        return getCpiMetadataEntity(cpiName, cpiVersion, signerSummaryHash)?.groupId
    }

    /**
     * For a given CPI, create the metadata entity required to insert into the database.
     *
     * @param cpi CPI object
     * @param cpiFileName original file name
     * @param checksum checksum/hash of the CPI
     * @param requestId the requestId originating from the chunk upload
     */
    @Suppress("LongParameterList")
    private fun createCpiMetadataEntity(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpiCpkEntities: List<Pair<String, CpiCpkEntity>>
    ): CpiMetadataEntity {
        val cpiMetadata = cpi.metadata

        return CpiMetadataEntity.create(
            name = cpiMetadata.cpiId.name,
            version = cpiMetadata.cpiId.version,
            signerSummaryHash = cpiMetadata.cpiId.signerSummaryHashForDbQuery,
            fileName = cpiFileName,
            fileChecksum = checksum.toString(),
            groupPolicy = cpi.metadata.groupPolicy!!,
            groupId = groupId,
            fileUploadRequestId = requestId,
            cpks = cpiCpkEntities
        )
    }

    private fun createOrUpdateCpkMetadataEntities(cpi: Cpi, existingCpks: Map<CpkKey, CpiCpkEntity>): List<Pair<String, CpiCpkEntity>> {
        return cpi.cpks.map { cpk ->
            val cpkKey = cpk.metadata.cpkId.toCpkKey()
            val existingCpiCpkEntity = existingCpks[cpkKey]

            val cpiCpkEntity: CpiCpkEntity = if(existingCpiCpkEntity != null) {
                updateExistingCpiCpkEntity(existingCpiCpkEntity, cpk)
            } else {
                createNewCpiCpkEntity(cpi, cpk, cpkKey)
            }
            cpk.originalFileName!! to cpiCpkEntity
        }
    }

    private fun createNewCpiCpkEntity(
        cpi: Cpi,
        cpk: Cpk,
        cpkKey: CpkKey
    ) = CpiCpkEntity(
        CpiCpkKey(
            cpi.metadata.cpiId.name,
            cpi.metadata.cpiId.version,
            cpi.metadata.cpiId.signerSummaryHash.toString(),
            cpk.metadata.cpkId.name,
            cpk.metadata.cpkId.version,
            cpk.metadata.cpkId.signerSummaryHash.toString(),
        ),
        cpk.originalFileName!!,
        cpk.metadata.fileChecksum.toString(),
        CpkMetadataEntity(
            cpkKey,
            cpk.metadata.fileChecksum.toString(),
            "1",
            cpk.metadata.toJsonAvro()
        )
    )

    private fun updateExistingCpiCpkEntity(
        existingCpiCpkEntity: CpiCpkEntity,
        cpk: Cpk
    ) = existingCpiCpkEntity.update(
        cpk.originalFileName ?: existingCpiCpkEntity.cpkFileName,
        cpk.metadata.fileChecksum.toString(),
        existingCpiCpkEntity.metadata.update(
            cpk.metadata.fileChecksum.toString(),
            "1",
            cpk.metadata.toJsonAvro(),
            false
        )
    )

    private fun createOrUpdateExistingCpkFileEntities(em: EntityManager, cpks: Collection<Cpk>): List<CpkFileEntity> {
        val query = """
                FROM ${CpkFileEntity::class.java.simpleName}
                WHERE id IN :cpkKeys
            """.trimIndent()

        val existingCpkFileEntities = em.createQuery(query, CpkFileEntity::class.java)
            .setParameter("cpkKeys", cpks.map { it.metadata.cpkId.toCpkKey() })
            .setLockMode(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
            .resultList.associateBy { it.id }

        return cpks.map { cpk ->
            val cpkKey = cpk.metadata.cpkId.toCpkKey()
            val existingCpkFileEntity = existingCpkFileEntities[cpkKey]

            existingCpkFileEntity?.update(
                cpk.metadata.fileChecksum.toString(),
                Files.readAllBytes(cpk.path!!)
            )
                ?: CpkFileEntity(
                    cpkKey,
                    cpk.metadata.fileChecksum.toString(),
                    Files.readAllBytes(cpk.path!!)
                )
        }
    }

    private fun CpkIdentifier.toCpkKey() = CpkKey(name, version, signerSummaryHash.toString())
}
