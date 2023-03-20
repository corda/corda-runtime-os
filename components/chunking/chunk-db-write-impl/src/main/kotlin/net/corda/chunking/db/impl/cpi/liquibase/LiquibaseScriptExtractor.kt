package net.corda.chunking.db.impl.cpi.liquibase

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import org.slf4j.LoggerFactory
import java.nio.file.Files

class LiquibaseScriptExtractor {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Extract liquibase scripts from the CPI.
     *
     * @return list of entities to be inserted into db containing liquibase scripts
     */
    fun extract(cpi: Cpi): List<CpkDbChangeLog> {
        log.info("Extracting liquibase scripts from for CPI: ${cpi.metadata.cpiId}")

        val liquiBaseScripts = cpi.cpks.flatMap { cpk -> extract(cpk) }

        if (liquiBaseScripts.isEmpty()) {
            log.warn("Extracting liquibase scripts finished although none were found for ${cpi.metadata.cpiId}")
        }

        return liquiBaseScripts
    }

    /**
     * Extract liquibase scripts from a CPK.
     *
     * @return the extracted liquibase scripts
     */
    private fun extract(cpk: Cpk): List<CpkDbChangeLog> {
        log.info("Extracting liquibase scripts from ${cpk.metadata.cpkId}")
        // We expect [Cpk.path] to be non-null here because it has been previously extracted to local disk above.
        val liquibaseScripts = Files.newInputStream(cpk.path!!).use { inputStream ->
            LiquibaseScriptExtractorHelpers().readLiquibaseScripts(cpk, inputStream)
        }
        log.info("Extracting liquibase scripts finished for ${cpk.metadata.cpkId}")

        return liquibaseScripts
    }
}
