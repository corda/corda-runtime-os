package net.corda.chunking.db.impl.cpi.liquibase

import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.v5.base.util.contextLogger
import java.nio.file.Files
import java.util.UUID

class LiquibaseExtractor {
    companion object {
        private val log = contextLogger()
    }

    /**
     * Extract liquibase scripts from the CPI and persist them to the database.
     *
     * @return list of entities to be inserted into db containing liquibase scripts
     */
    fun extractLiquibaseScriptsFromCpi(cpi: Cpi) : List<CpkDbChangeLogEntity> {
        log.info("Extracting liquibase files from for CPI: ${cpi.metadata.cpiId}")

        val changeUUID = UUID.randomUUID()
        val entities = cpi.cpks.flatMap { extractLiquibaseFromCpk(it, changeUUID) }

        if (entities.isEmpty()) {
            log.warn("Extracting liquibase finished although none were found for ${cpi.metadata.cpiId}")
        }

        return entities
    }

    /**
     * Extract liquibase scripts from a CPK and persist them to the database.
     *
     * @return the extracted entities containing the liquibase scripts
     */
    private fun extractLiquibaseFromCpk(cpk: Cpk, changeUUID: UUID) : List<CpkDbChangeLogEntity> {
        log.info("Extracting liquibase files from ${cpk.metadata.cpkId}")
        // We expect [Cpk.path] to be non-null here because it has been previously extracted to local disk above.
        val entities = Files.newInputStream(cpk.path!!).use {
            LiquibaseExtractorHelpers().getEntities(cpk, it, changeUUID)
        }
        log.info("Extracting liquibase files finished for ${cpk.metadata.cpkId}")
        return entities
    }
}
