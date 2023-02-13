package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkChangeLogIdentifier
import net.corda.libs.packaging.core.CpiIdentifier
import javax.persistence.EntityManager

/**
 * Interface for CRUD operations for cpk database change log
 */
interface CpkDbChangeLogRepository {
    fun put(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog)
    fun update(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog)

    fun findByFileChecksum(em: EntityManager, cpkFileChecksums: Set<String>): List<CpkDbChangeLog>

    fun findByContent(em: EntityManager, content: String): List<CpkDbChangeLog>

    fun findByCpiId(em: EntityManager, cpiIdentifier: CpiIdentifier): List<CpkDbChangeLog>
    fun findById(em: EntityManager, cpkChangeLogIdentifier: CpkChangeLogIdentifier): CpkDbChangeLog
}
