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
import net.corda.libs.cpi.datamodel.CpkCordappManifestEntity
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.libs.cpi.datamodel.CpkDependencyEntity
import net.corda.libs.cpi.datamodel.CpkFormatVersion
import net.corda.libs.cpi.datamodel.CpkManifest
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.ManifestCorDappInfo
import net.corda.orm.utils.transaction
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * This class provides some simple APIs to interact with the database.
 */
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
        val entity = entityManagerFactory.createEntityManager().transaction {
            it.find(CpkDataEntity::class.java, cpkChecksum.toString())
        }

        return entity != null
    }

    override fun cpiExists(cpiName: String, cpiVersion: String, signerSummaryHash: String): Boolean =
        getCpiMetadataEntity(cpiName, cpiVersion, signerSummaryHash) != null

    override fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String
    ) {
        val cpiMetadataEntity = createCpiMetadataEntity(cpi, cpiFileName, checksum, requestId, groupId)
        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadataEntity)
            storeCpkContentInTransaction(cpi.cpks, em, cpiMetadataEntity)
        }
    }

    private fun storeCpkContentInTransaction(
        cpks: Collection<Cpk>,
        em: EntityManager,
        cpiMetadataEntity: CpiMetadataEntity
    ) {
        cpks.forEach {
            val cpkChecksum = it.metadata.fileChecksum.toString()
            // Using `merge` here as exactly the same CPK data may already exist
            em.merge(CpkDataEntity(cpkChecksum, Files.readAllBytes(it.path!!)))

            val cpkMetadataEntity = CpkMetadataEntity(
                cpi = cpiMetadataEntity,
                cpkFileChecksum = cpkChecksum,
                cpkFileName = it.originalFileName!!,
                mainBundleName = it.metadata.cpkId.name,
                mainBundleVersion = it.metadata.cpkId.version,
                signerSummaryHash = it.metadata.cpkId.signerSummaryHashForDbQuery,
                cpkManifest = CpkManifest(
                    CpkFormatVersion(
                        it.metadata.manifest.cpkFormatVersion.major,
                        it.metadata.manifest.cpkFormatVersion.minor
                    )
                ),
                cpkMainBundle = it.metadata.mainBundle,
                cpkType = it.metadata.type.name,
                cpkLibraries = it.metadata.libraries
            )
            em.persist(cpkMetadataEntity)

            it.metadata.dependencies.forEach { cpkIdentifier ->
                val cpkDependencyEntity = CpkDependencyEntity(
                    cpkMetadataEntity = cpkMetadataEntity,
                    mainBundleName = cpkIdentifier.name,
                    mainBundleVersion = cpkIdentifier.version,
                    signerSummaryHash = cpkIdentifier.signerSummaryHashForDbQuery
                )
                em.persist(cpkDependencyEntity)
            }

            it.metadata.cordappManifest.run {
                val cpkCordappManifestEntity = CpkCordappManifestEntity(
                    cpkMetadataEntity = cpkMetadataEntity,
                    bundleSymbolicName = this.bundleSymbolicName,
                    bundleVersion = this.bundleVersion,
                    minPlatformVersion = this.minPlatformVersion,
                    targetPlatformVersion = this.targetPlatformVersion,
                    contractInfo = ManifestCorDappInfo(
                        shortName = this.contractInfo.shortName,
                        vendor = this.contractInfo.vendor,
                        versionId = this.contractInfo.versionId,
                        license = this.contractInfo.licence
                    ),
                    workflowInfo = ManifestCorDappInfo(
                        shortName = this.workflowInfo.shortName,
                        vendor = this.workflowInfo.vendor,
                        versionId = this.workflowInfo.versionId,
                        license = this.workflowInfo.licence
                    )
                )
                em.persist(cpkCordappManifestEntity)
            }
        }
    }

    override fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String
    ) {
        val cpiId = cpi.metadata.cpiId
        log.info("Performing updateMetadataAndCpks for: ${cpiId.name} v${cpiId.version}")

        // Delete CPK metadata in separate transaction, to be reviewed
        entityManagerFactory.createEntityManager().transaction { em ->
            val cpkMetadataStream = em.createQuery(
                "FROM ${CpkMetadataEntity::class.simpleName} WHERE " +
                        "cpi_name = :cpi_name AND cpi_version = :cpi_version AND cpi_signer_summary_hash = :cpi_signer_summary_hash",
                CpkMetadataEntity::class.java
            )
                .setParameter("cpi_name", cpiId.name)
                .setParameter("cpi_version", cpiId.version)
                .setParameter("cpi_signer_summary_hash", cpiId.signerSummaryHashForDbQuery)
                .resultStream

            cpkMetadataStream.use { stream ->
                stream.forEach { cpkMeta ->
                    // To be reviewed after more changes merged, and we will be able to update the version and timestamp
                    // on CPK metadata
                    em.remove(em.merge(cpkMeta.cpkCordappManifest))
                    cpkMeta.cpkDependencies.forEach { em.remove(em.merge(it)) }
                    em.remove(em.merge(cpkMeta))
                }
            }
        }

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

            existingMetadataEntity.entityVersion++
            existingMetadataEntity.fileUploadRequestId = requestId
            existingMetadataEntity.insertTimestamp = Instant.now()
            existingMetadataEntity.fileName = cpiFileName
            existingMetadataEntity.fileChecksum = checksum.toString()
            existingMetadataEntity.groupPolicy = cpi.metadata.groupPolicy!!
            existingMetadataEntity.groupId = groupId

            // Perform update
            em.merge(existingMetadataEntity)

            // Store CPK data
            storeCpkContentInTransaction(cpi.cpks, em, existingMetadataEntity)
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
        return entityManager.find(CpiMetadataEntity::class.java, primaryKey)
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
    private fun createCpiMetadataEntity(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String
    ): CpiMetadataEntity {
        val cpiMetadata = cpi.metadata

        return CpiMetadataEntity(
            name = cpiMetadata.cpiId.name,
            version = cpiMetadata.cpiId.version,
            signerSummaryHash = cpiMetadata.cpiId.signerSummaryHashForDbQuery,
            fileName = cpiFileName,
            fileChecksum = checksum.toString(),
            groupPolicy = cpi.metadata.groupPolicy!!,
            groupId = groupId,
            fileUploadRequestId = requestId,
            isDeleted = false
        )
    }
}
