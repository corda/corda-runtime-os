package net.corda.chunking.db.impl

import net.corda.chunking.ChunkReaderFactory
import net.corda.chunking.RequestId
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.data.chunking.UploadFileStatus
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.packaging.CpiMetadata
import net.corda.packaging.CPI
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.persistence.PersistenceException

class CpiValidatorImpl(
    private val queries: DatabaseQueries,
    private val cpiInfoWriteService: CpiInfoWriteService
) : CpiValidator {
    companion object {
        private val log = contextLogger()
    }

    private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "unpacking")
        .apply { Files.createDirectories(this) }

    private val cpiExpansionDir = Paths.get(System.getProperty("java.io.tmpdir"), "expanded")
        .apply { Files.createDirectories(this) }

    /**
     * Assembles the CPI from chunks in the database, and returns the temporary path
     * that we've stored the recreated binary, plus any filename associated with it.
     *
     * Any values returned that are null can be considered a failure.
     *
     * @return  `originalFileName: String?` , `tempPathToCpi: Path?` and `checksum: SecureHash?`
     */
    private fun getCpi(requestId: RequestId /* = kotlin.String */): Triple<String?, Path?, SecureHash?> {
        var fileName: String? = null
        var tempPath: Path? = null
        var checksum: SecureHash? = null

        // Set up chunk reader.  If onComplete is never called, we've failed.
        val reader = ChunkReaderFactory.create(tmpDir).apply {
            onComplete { originalFileName: String, tempPathOfBinary: Path, fileChecksum: SecureHash ->
                fileName = originalFileName
                tempPath = tempPathOfBinary
                checksum = fileChecksum
            }
        }

        // Now read chunks, and create CPI on local disk
        queries.forEachChunk(requestId, reader::read)

        return Triple(fileName, tempPath, checksum)
    }

    @Suppress("ComplexMethod")
    override fun validate(requestId: RequestId): UploadFileStatus {
        log.debug("Validating $requestId")

        val (cpiFileName, tempPath, checksum) = getCpi(requestId)

        if ((cpiFileName == null) || (tempPath == null) || (checksum == null)) {
            log.error("Did not combine all chunks to produce file for $requestId")
            return UploadFileStatus.FILE_INVALID
        }

        if (!Files.newInputStream(tempPath).use { isSigned(it) }) {
            log.error("Signature invalid: $cpiFileName")
            return UploadFileStatus.FILE_INVALID
        }

        // Loads, parses, and expands CPI, into cpks and metadata
        val cpi = Files.newInputStream(tempPath).use { CPI.from(it, cpiExpansionDir) }

        // Can't necessarily compare the CPI.metadata.hash to our checksum above
        // because two different digest algorithms might have been used to create them.
        // We'll publish to the database using the de-chunking checksum
        try {
            val cpiMetadataEntity = createCpiMetadataEntity(cpi, cpiFileName, checksum, requestId)

            if (!queries.containsCpiByPrimaryKey(cpiMetadataEntity)) {
                queries.persistMetadataAndCpks(cpiMetadataEntity, cpi.cpks)
            } else {
                log.warn("CPI has already been inserted with cpks for $cpiMetadataEntity")
            }
        } catch (ex: Exception) {
            return when (ex) {
                is PersistenceException -> {
                    log.error("Error when trying to persist CPI and CPK to database", ex)
                    UploadFileStatus.UPLOAD_FAILED
                }
                is CordaRuntimeException -> {
                    log.error("Error when trying to persist CPI and CPK to database", ex)
                    UploadFileStatus.UPLOAD_FAILED
                }
                else -> throw ex
            }
        }

        // Lastly publish the CPI info to Kafka.
        cpiInfoWriteService.put(CpiMetadata.fromLegacy(cpi))

        return UploadFileStatus.OK
    }

    /**
     * For a given CPI create the metadata entity required to insert into the database.
     *
     * @param cpi CPI object
     * @param cpiFileName original file name
     * @param checksum checksum/hash of the CPI
     * @param requestId the requestId originating from the chunk upload
     */
    private fun createCpiMetadataEntity(
        cpi: CPI,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId
    ): CpiMetadataEntity {
        val groupId = getGroupId(cpi)

        val cpiMetadata = cpi.metadata

        return CpiMetadataEntity(
            cpiMetadata.id.name,
            cpiMetadata.id.version,
            cpiMetadata.id.signerSummaryHash?.toString() ?: "",
            cpiFileName,
            checksum.toString(),
            cpi.metadata.groupPolicy!!,
            groupId,
            requestId
        )
    }

    /**
     * Get groupId from group policy JSON on the [CPI] object.
     *
     * @throws CordaRuntimeException if there is no group policy json.
     * @return `groupId`
     */
    private fun getGroupId(cpi: CPI): String {
        if (cpi.metadata.groupPolicy == null) throw CordaRuntimeException("CPI is missing a group policy file")
        return GroupPolicyParser.groupId(cpi.metadata.groupPolicy!!)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun isSigned(cpiInputStream: InputStream): Boolean {
        // STUB:  we need to implement this (can change function argument to Path).
        // The CPI loading code has some signature validation in it, so this stub may be unnecessary.
        return true
    }
}
