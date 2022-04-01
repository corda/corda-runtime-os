package net.corda.chunking.db.impl.validation

import net.corda.chunking.ChunkReaderFactory
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.packaging.CPI
import net.corda.packaging.PackagingException
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.persistence.PersistenceException

class ValidationFunctions(
    private val cpiCacheDir: Path,
    private val cpiPartsDir: Path
) {
    /**
     * Assembles the CPI from chunks in the database, and returns the temporary path
     * that we've stored the recreated binary, plus any filename associated with it.
     *
     * Any values returned that are null can be considered a failure.
     *
     * @throws ValidationException
     */
    fun getFileInfo(persistence: ChunkPersistence, requestId: RequestId): FileInfo {
        var fileName: String? = null
        var tempPath: Path? = null
        var checksum: SecureHash? = null

        // Set up chunk reader.  If onComplete is never called, we've failed.
        val reader = ChunkReaderFactory.create(cpiCacheDir).apply {
            onComplete { originalFileName: String, tempPathOfBinary: Path, fileChecksum: SecureHash ->
                fileName = originalFileName
                tempPath = tempPathOfBinary
                checksum = fileChecksum
            }
        }

        // Now read chunks, and create CPI on local disk
        persistence.forEachChunk(requestId, reader::read)

        if (fileName == null || tempPath == null || checksum == null) {
            throw ValidationException("Did not combine all chunks to produce file for $requestId")
        }

        return FileInfo(fileName!!, tempPath!!, checksum!!)
    }

    /** Loads, parses, and expands CPI, into cpks and metadata */
    fun checkCpi(cpiFile: FileInfo): CPI {
        val cpi: CPI =
            try {
                Files.newInputStream(cpiFile.path).use { CPI.from(it, cpiPartsDir) }
            } catch (ex: Exception) {
                when (ex) {
                    is PackagingException -> {
                        throw ValidationException("Invalid CPI.  ${ex.message}", ex)
                    }
                    else -> {
                        throw ValidationException("Unexpected exception when unpacking CPI.  ${ex.message}", ex)
                    }
                }
            }
        return cpi
    }

    /**
     * Persists CPI metadata and CPK content.
     *
     * @throws ValidationException if there is an error
     */
    @Suppress("ThrowsCount")
    fun persistToDatabase(
        chunkPersistence: ChunkPersistence,
        cpi: CPI,
        fileInfo: FileInfo,
        requestId: RequestId
    ) {
        // Cannot compare the CPI.metadata.hash to our checksum above
        // because two different digest algorithms might have been used to create them.
        // We'll publish to the database using the de-chunking checksum.

        try {
            val groupId = getGroupId(cpi)

            if (!chunkPersistence.cpiExists(
                    cpi.metadata.id.name,
                    cpi.metadata.id.version,
                    cpi.metadata.id.signerSummaryHash?.toString() ?: ""
                )
            ) {
                chunkPersistence.persistMetadataAndCpks(cpi, fileInfo.name, fileInfo.checksum, requestId, groupId)
            } else {
                throw ValidationException(
                    "CPI has already been inserted with cpks for " +
                            "${cpi.metadata.id.name} ${cpi.metadata.id.version} with groupId=$groupId"
                )
            }
        } catch (ex: Exception) {
            when (ex) {
                is ValidationException -> throw ex
                is PersistenceException -> throw ValidationException("Could not persist CPI and CPK to database", ex)
                is CordaRuntimeException -> throw ValidationException("Could not persist CPI and CPK to database", ex)
                else -> throw ValidationException("Unexpected error when trying to persist CPI and CPK to database", ex)
            }
        }
    }


    /**
     * Get groupId from group policy JSON on the [CPI] object.
     *
     * @throws CordaRuntimeException if there is no group policy json.
     * @return `groupId`
     */
    fun getGroupId(cpi: CPI): String {
        if (cpi.metadata.groupPolicy.isNullOrEmpty()) throw ValidationException("CPI is missing a group policy file")
        return GroupPolicyParser.groupId(cpi.metadata.groupPolicy!!)
    }

    /**
     * @throws ValidationException if the signature is incorrect
     */
    fun checkSignature(file: FileInfo) {
        if (!Files.newInputStream(file.path).use { isSigned(it) }) {
            throw ValidationException("Signature invalid: ${file.name}")
        }
    }

    /**
     * STUB - this needs to be implemented
     */
    @Suppress("UNUSED_PARAMETER")
    private fun isSigned(cpiInputStream: InputStream): Boolean {
        // STUB:  we need to implement this (can change function argument to Path).
        // The CPI loading code has some signature validation in it, so this stub may be unnecessary.
        return true
    }

    fun checkGroupIdDoesNotExistForThisCpi(persistence: ChunkPersistence, cpi: CPI) {
        val groupIdInDatabase = persistence.getGroupId(
            cpi.metadata.id.name,
            cpi.metadata.id.version,
            cpi.metadata.id.signerSummaryHash?.toString() ?: ""
        )
        if (groupIdInDatabase != null) {
            throw ValidationException("CPI already uploaded with groupId = $groupIdInDatabase")
        }
    }
}
