package net.corda.chunking.db.impl.validation

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.packaging.CpiMetadata
import net.corda.packaging.CPI
import net.corda.v5.base.util.contextLogger

class CpiValidatorImpl(
    private val persistence: ChunkPersistence,
    private val cpiInfoWriteService: CpiInfoWriteService
) : CpiValidator {
    companion object {
        private val log = contextLogger()
    }

    override fun validate(requestId: RequestId) {
        //  Each function may throw a [ValidationException]
        log.debug("Validating $requestId")

        val fileInfo = ValidationFunctions.getFileInfo(persistence, requestId)

        ValidationFunctions.checkSignature(fileInfo)

        val cpi: CPI = ValidationFunctions.checkCpi(fileInfo)

        ValidationFunctions.checkGroupIdDoesNotExistForThisCpi(persistence, cpi)

        ValidationFunctions.persistToDatabase(persistence, cpi, fileInfo, requestId)

        // Lastly publish the CPI info to Kafka.
        cpiInfoWriteService.put(CpiMetadata.fromLegacy(cpi))
    }
}
