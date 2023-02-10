package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import javax.persistence.EntityManager

/**
 * Interface for CRUD operations for cpk database change log
 */
interface CpkDbChangeLogAuditRepository {
    fun put(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog)
}