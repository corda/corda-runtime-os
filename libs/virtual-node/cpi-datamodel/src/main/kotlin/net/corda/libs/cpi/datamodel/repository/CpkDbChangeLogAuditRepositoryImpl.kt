package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.entities.CpkDbChangeLogAuditEntity
import java.util.*
import javax.persistence.EntityManager

class CpkDbChangeLogAuditRepositoryImpl: CpkDbChangeLogAuditRepository {
    override fun put(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog) {
        em.persist(cpkDbChangeLog.toEntity())
    }

    /**
     * Converts a data transport object to an entity.
     */
    private fun CpkDbChangeLog.toEntity(): CpkDbChangeLogAuditEntity {
        return CpkDbChangeLogAuditEntity(
            UUID.randomUUID().toString(),
            fileChecksum,
            filePath,
            content
        )
    }
}