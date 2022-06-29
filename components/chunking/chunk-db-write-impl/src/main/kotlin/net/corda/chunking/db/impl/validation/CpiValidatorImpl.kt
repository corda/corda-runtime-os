package net.corda.chunking.db.impl.validation

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.file.Path
import net.corda.chunking.db.impl.persistence.CpiPersistence

@Suppress("LongParameterList")
class CpiValidatorImpl constructor(
    private val publisher: StatusPublisher,
    chunkPersistence: ChunkPersistence,
    cpiPersistence: CpiPersistence,
    private val cpiInfoWriteService: CpiInfoWriteService,
    cpiCacheDir: Path,
    cpiPartsDir: Path,
    private val clock: Clock
) : CpiValidator {
    companion object {
        private val log = contextLogger()
    }

    private val validationFunctions = ValidationFunctions(cpiCacheDir, cpiPartsDir, chunkPersistence, cpiPersistence)

    override fun validate(requestId: RequestId): SecureHash {
        //  Each function may throw a [ValidationException]
        log.debug("Validating $requestId")

        // Assemble the CPI locally and return information about it
        publisher.update(requestId, "Validating upload")
        val fileInfo = validationFunctions.getFileInfo(requestId)

        publisher.update(requestId, "Checking signatures")
        validationFunctions.checkSignature(fileInfo)

        publisher.update(requestId, "Validating CPI")
        val cpi: Cpi = validationFunctions.checkCpi(fileInfo)

        publisher.update(requestId, "Checking group id in CPI")
        validationFunctions.getGroupId(cpi)

        if (!fileInfo.forceUpload) {
            publisher.update(requestId, "Validating group id against DB")
            validationFunctions.checkGroupIdDoesNotExistForThisCpi(cpi)
        }

        publisher.update(requestId, "Extracting Liquibase files from CPKs in CPI")
        val cpkDbChangeLogEntities = validationFunctions.extractLiquibaseScriptsFromCpi(cpi)

        publisher.update(requestId, "Persisting CPI")
        val cpiMetadataEntity = validationFunctions.persistToDatabase(
            cpi,
            fileInfo,
            requestId,
            cpkDbChangeLogEntities
        )

        publisher.update(requestId, "Notifying flow workers")
        val cpiMetadata = CpiMetadata(
            cpi.metadata.cpiId,
            fileInfo.checksum,
            cpi.cpks.map { it.metadata },
            cpi.metadata.groupPolicy,
            version = cpiMetadataEntity.entityVersion,
            timestamp = clock.instant()
        )
        cpiInfoWriteService.put(cpiMetadata.cpiId, cpiMetadata)

        return fileInfo.checksum
    }
}
