package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.entities.CpkDbChangeLogEntity
import net.corda.libs.packaging.core.CpiIdentifier
import javax.persistence.EntityManager

/**
 * Interface for CRUD operations for cpk database change log
 */
interface CpkDbChangeLogRepository {
    fun update(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog)

    fun findByFileChecksum(em: EntityManager, cpkFileChecksums: Set<String>): List<CpkDbChangeLog>
    fun findByCpiId(em: EntityManager, cpiIdentifier: CpiIdentifier): List<CpkDbChangeLog>
}
