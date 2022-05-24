package net.corda.chunking.db.impl.validation

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.file.Path
import java.time.Instant

class CpiValidatorImpl(
    private val publisher: StatusPublisher,
    private val persistence: ChunkPersistence,
    private val cpiInfoWriteService: CpiInfoWriteService,
    cpiCacheDir: Path,
    cpiPartsDir: Path
) : CpiValidator {
    companion object {
        private val log = contextLogger()
    }

    private val validationFunctions = ValidationFunctions(cpiCacheDir, cpiPartsDir)

    override fun validate(requestId: RequestId): SecureHash {
        //  Each function may throw a [ValidationException]
        log.debug("Validating $requestId")

        publisher.update(requestId, "Validating upload")
        val fileInfo = validationFunctions.getFileInfo(persistence, requestId)

        publisher.update(requestId, "Checking signatures")
        validationFunctions.checkSignature(fileInfo)

        publisher.update(requestId, "Validating CPI")
        val cpi: Cpi = validationFunctions.checkCpi(fileInfo)

        publisher.update(requestId, "Checking group id in CPI")
        validationFunctions.getGroupId(cpi)

        if (!fileInfo.forceUpload) {
            publisher.update(requestId, "Validating group id against DB")
            validationFunctions.checkGroupIdDoesNotExistForThisCpi(persistence, cpi)
        }

        publisher.update(requestId, "Persisting CPI")
        validationFunctions.persistToDatabase(persistence, cpi, fileInfo, requestId)

        publisher.update(requestId, "Notifying flow workers")
        val timestamp = Instant.now()
        val cpiMetadata = CpiMetadata(
            cpi.metadata.cpiId,
            fileInfo.checksum,
            cpi.cpks.map { it.metadata },
            cpi.metadata.groupPolicy,
            // TODO the below version should be populated from the DB as per https://r3-cev.atlassian.net/browse/CORE-4890
            version = 0,
            timestamp
        )
        cpiInfoWriteService.put(cpiMetadata)

        return fileInfo.checksum
    }
}
