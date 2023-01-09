package net.corda.chunking.db.impl.validation

import net.corda.chunking.ChunkReaderFactory
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.cpi.liquibase.LiquibaseExtractor
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.CpiReader
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.membership.lib.grouppolicy.GroupPolicyIdNotFoundException
import net.corda.membership.lib.grouppolicy.GroupPolicyParseException
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import javax.persistence.PersistenceException

/**
 * Assembles the CPI from chunks in the database, and returns the temporary path
 * that we've stored the recreated binary, plus any filename associated with it.
 *
 * Any values returned that are null can be considered a failure.
 *
 * @throws ValidationException
 */
fun assembleFileFromChunks(
    cacheDir: Path,
    chunkPersistence: ChunkPersistence,
    requestId: RequestId,
    chunkReaderFactory: ChunkReaderFactory
): FileInfo {
    var fileName: String? = null
    lateinit var tempPath: Path
    lateinit var checksum: SecureHash
    var localProperties: Map<String, String?>? = null

    // Set up chunk reader.  If onComplete is never called, we've failed.
    val reader = chunkReaderFactory.create(cacheDir).apply {
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
            throw ValidationException("Did not combine all chunks to produce file", requestId)
        }

        FileInfo(this, tempPath, checksum, localProperties)
    }
}

/**
 * Loads, parses, and expands CPI, into cpks and metadata
 *
 * @throws ValidationException
 */
fun FileInfo.validateAndGetCpi(cpiPartsDir: Path, requestId: String): Cpi {
    val cpi: Cpi =
        try {
            Files.newInputStream(this.path).use { CpiReader.readCpi(it, cpiPartsDir) }
        } catch (ex: Exception) {
            when (ex) {
                is PackagingException -> {
                    throw ValidationException("Invalid CPI.  ${ex.message}", requestId, ex)
                }

                else -> {
                    throw ValidationException("Unexpected exception when unpacking CPI.  ${ex.message}", requestId, ex)
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
@Suppress("ThrowsCount", "ComplexMethod", "LongParameterList")
fun CpiPersistence.persistCpiToDatabase(
    cpi: Cpi,
    groupId: String,
    fileInfo: FileInfo,
    requestId: RequestId,
    changelogsExtractedFromCpi: List<CpkDbChangeLogEntity>,
    log: Logger
): CpiMetadataEntity {
    // Cannot compare the CPI.metadata.hash to our checksum above
    // because two different digest algorithms might have been used to create them.
    // We'll publish to the database using the de-chunking checksum.

    try {
        val cpiExists = this.cpiExists(
            cpi.metadata.cpiId.name,
            cpi.metadata.cpiId.version,
            cpi.metadata.cpiId.signerSummaryHashForDbQuery
        )

        return if (cpiExists && fileInfo.forceUpload) {
            log.info("Force uploading CPI: ${cpi.metadata.cpiId.name} v${cpi.metadata.cpiId.version}")
            this.updateMetadataAndCpks(
                cpi,
                fileInfo.name,
                fileInfo.checksum,
                requestId,
                groupId,
                changelogsExtractedFromCpi
            )
        } else if (!cpiExists) {
            log.info("Uploading CPI: ${cpi.metadata.cpiId.name} v${cpi.metadata.cpiId.version}")
            this.persistMetadataAndCpks(
                cpi,
                fileInfo.name,
                fileInfo.checksum,
                requestId,
                groupId,
                changelogsExtractedFromCpi
            )
        } else {
            throw UnsupportedOperationException(
                "CPI ${cpi.metadata.cpiId.name} ${cpi.metadata.cpiId.version} ${cpi.metadata.cpiId.signerSummaryHashForDbQuery} " +
                        "already exists and cannot be replaced."
            )
        }
    } catch (ex: Exception) {
        log.info("Unexpected error when persisting CPI to the database", ex)
        when (ex) {
            is PersistenceException -> throw ValidationException("Could not persist CPI and CPK to database", requestId, ex)
            is CordaRuntimeException -> throw ValidationException("Could not persist CPI and CPK to database", requestId, ex)
            else -> throw ValidationException("Unexpected error when trying to persist CPI and CPK to database", requestId, ex)
        }
    }
}

/**
 * Checks that a CPI has a group policy.
 *
 * @throws ValidationException if there is no group policy json.
 */
private fun Cpi.validateHasGroupPolicy(requestId: String? = null) {
    if (this.metadata.groupPolicy.isNullOrEmpty()) throw ValidationException("CPI is missing a group policy file", requestId)
}

/**
 * Get groupId from group policy JSON on the [Cpi] object.
 *
 * @param getGroupIdFromJson lambda that takes a json string and returns the `groupId`
 *
 * @throws ValidationException if there is no group policy json.
 * @throws CordaRuntimeException if there is an error parsing the group policy json.
 * @return `groupId`
 */
@Suppress("ThrowsCount")
fun Cpi.validateAndGetGroupId(requestId: String, getGroupIdFromJson: (String) -> String): String {
    validateHasGroupPolicy(requestId)
    val groupId = try {
        getGroupIdFromJson(this.metadata.groupPolicy!!)
        // catch specific exceptions, and wrap them up so as to capture the request ID
        // This exception will end up going over Kafka and being picked up by the RPC worker,
        // which then matches by class name,  so we cannot use subtypes of ValidationException without
        // introducing knowledge of specific failure modes into the RPC worker
    } catch (e: GroupPolicyIdNotFoundException) {
        throw ValidationException("Unable to upload CPI due to group ID not found", requestId)
    } catch (e: GroupPolicyParseException) {
        throw ValidationException("Unable to upload CPI due to group policy parse error ${e.message}", requestId, e)
    }
    if (groupId.isBlank()) throw ValidationException("Unable to upload CPI due to group ID being blank", requestId)
    return groupId
}

/**
 * Get fileFormatVersion from group policy JSON on the [Cpi] object.
 *
 * @throws ValidationException if there is no group policy json.
 * @throws CordaRuntimeException if there is an error parsing the group policy json.
 * @return the group policy file format version
 */
fun Cpi.validateAndGetGroupPolicyFileVersion(): Int {
    validateHasGroupPolicy()
    return try {
        GroupPolicyParser.getFileFormatVersion(this.metadata.groupPolicy!!)
    } catch (e: Exception) {
        throw ValidationException("Group policy file in the CPI is invalid. Could not get file format version. ${e.message}", null, e)
    }
}

/**
 * Extract liquibase scripts from the CPI and persist them to the database.
 *
 * @return list of entities containing liquibase scripts ready for insertion into database
 */
fun Cpi.extractLiquibaseScripts(): List<CpkDbChangeLogEntity> =
    LiquibaseExtractor().extractLiquibaseEntitiesFromCpi(this)
