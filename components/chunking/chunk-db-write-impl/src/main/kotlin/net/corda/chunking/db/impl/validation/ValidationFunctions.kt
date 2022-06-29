package net.corda.chunking.db.impl.validation

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.persistence.PersistenceException
import net.corda.chunking.ChunkReaderFactory
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.cpi.liquibase.LiquibaseExtractor
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.CpiReader
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger

/**
 * Assembles the CPI from chunks in the database, and returns the temporary path
 * that we've stored the recreated binary, plus any filename associated with it.
 *
 * Any values returned that are null can be considered a failure.
 *
 * @throws ValidationException
 */
fun Path.getFileInfo(chunkPersistence: ChunkPersistence, requestId: RequestId): FileInfo {
    val cpiCacheDir = this
    var fileName: String? = null
    lateinit var tempPath: Path
    lateinit var checksum: SecureHash
    var localProperties: Map<String, String?>? = null

    // Set up chunk reader.  If onComplete is never called, we've failed.
    val reader = ChunkReaderFactory.create(cpiCacheDir).apply {
        onComplete { originalFileName: String, tempPathOfBinary: Path, fileChecksum: SecureHash, properties: Map<String, String?>? ->
            fileName = originalFileName
            tempPath = tempPathOfBinary
            checksum = fileChecksum
            localProperties = properties
        }
    }

    // Now read chunks, and create CPI on local disk
    chunkPersistence.forEachChunk(requestId, reader::read)

    return with(fileName) {
        if (this == null) {
            throw ValidationException("Did not combine all chunks to produce file for $requestId")
        }

        FileInfo(this, tempPath, checksum, localProperties)
    }
}

/** Loads, parses, and expands CPI, into cpks and metadata */
fun FileInfo.validateAndGetCpi(cpiPartsDir: Path): Cpi {
    val cpi: Cpi =
        try {
            Files.newInputStream(this.path).use { CpiReader.readCpi(it, cpiPartsDir) }
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
@Suppress("ThrowsCount", "ComplexMethod")
fun Cpi.persistToDatabase(
    cpiPersistence: CpiPersistence,
    fileInfo: FileInfo,
    requestId: RequestId,
    cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>,
    log: Logger
): CpiMetadataEntity {
    // Cannot compare the CPI.metadata.hash to our checksum above
    // because two different digest algorithms might have been used to create them.
    // We'll publish to the database using the de-chunking checksum.

    val cpi = this
    try {
        val groupId = cpi.validateAndGetGroupId()

        val cpiExists = cpiPersistence.cpiExists(
            cpi.metadata.cpiId.name,
            cpi.metadata.cpiId.version,
            cpi.metadata.cpiId.signerSummaryHashForDbQuery
        )

        return if (cpiExists && fileInfo.forceUpload) {
            log.info("Force uploading CPI: ${cpi.metadata.cpiId.name} v${cpi.metadata.cpiId.version}")
            cpiPersistence.updateMetadataAndCpks(cpi, fileInfo.name, fileInfo.checksum, requestId, groupId, cpkDbChangeLogEntities)
        } else if (!cpiExists) {
            log.info("Uploading CPI: ${cpi.metadata.cpiId.name} v${cpi.metadata.cpiId.version}")
            cpiPersistence.persistMetadataAndCpks(cpi, fileInfo.name, fileInfo.checksum, requestId, groupId, cpkDbChangeLogEntities)
        } else {
            throw ValidationException(
                "CPI has already been inserted with cpks for " +
                        "${cpi.metadata.cpiId.name} ${cpi.metadata.cpiId.version} with groupId=$groupId"
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
 * Get groupId from group policy JSON on the [Cpi] object.
 *
 * @throws CordaRuntimeException if there is no group policy json.
 * @return `groupId`
 */
fun Cpi.validateAndGetGroupId(): String {
    if (this.metadata.groupPolicy.isNullOrEmpty()) throw ValidationException("CPI is missing a group policy file")
    return GroupPolicyParser.groupId(this.metadata.groupPolicy!!)
}

/**
 * @throws ValidationException if the signature is incorrect
 */
fun FileInfo.checkSignature() {
    if (!Files.newInputStream(this.path).use { isSigned(it) }) {
        throw ValidationException("Signature invalid: ${this.name}")
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

fun Cpi.checkGroupIdDoesNotExist(cpiPersistence: CpiPersistence) {
    val groupIdInDatabase = cpiPersistence.getGroupId(
        this.metadata.cpiId.name,
        this.metadata.cpiId.version,
        this.metadata.cpiId.signerSummaryHashForDbQuery
    )

    if (groupIdInDatabase != null) {
        throw ValidationException("CPI already uploaded with groupId = $groupIdInDatabase")
    }
}

/**
 * Extract liquibase scripts from the CPI and persist them to the database.
 *
 * @return list of entities containing liquibase scripts ready for insertion into database
 */
fun Cpi.extractLiquibaseScripts(): List<CpkDbChangeLogEntity> =
    LiquibaseExtractor().extractLiquibaseEntitiesFromCpi(this)
