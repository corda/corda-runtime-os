package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import javax.persistence.EntityManager

/**
 * Interface for CRUD operations for cpk database change log
 */
interface CpkDbChangeLogAuditRepository {
    fun put(em: EntityManager, changeLogAudit: CpkDbChangeLogAudit)

    fun findById(em: EntityManager, id: String): CpkDbChangeLogAudit

    fun findByFileChecksums(em: EntityManager, cpkFileChecksums: List<String>): List<CpkDbChangeLogAudit>
}