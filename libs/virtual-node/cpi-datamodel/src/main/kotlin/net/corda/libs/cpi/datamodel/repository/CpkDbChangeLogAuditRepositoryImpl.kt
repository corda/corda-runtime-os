package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import net.corda.libs.cpi.datamodel.entities.CpkDbChangeLogAuditEntity
import javax.persistence.EntityManager
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier

class CpkDbChangeLogAuditRepositoryImpl: CpkDbChangeLogAuditRepository {
    override fun put(em: EntityManager, changeLogAudit: CpkDbChangeLogAudit) {
        em.persist(changeLogAudit.toEntity())
    }

    override fun findById(em: EntityManager, id: String): CpkDbChangeLogAudit {
        val entity = em.find(CpkDbChangeLogAuditEntity::class.java, id);
        return entity.toDto()
    }

    override fun findByFileChecksums(em: EntityManager, cpkFileChecksums: List<String>): List<CpkDbChangeLogAudit> {
        return em.createQuery("FROM ${CpkDbChangeLogAuditEntity::class.java.simpleName} where cpkFileChecksum IN :checksums")
            .setParameter("checksums", cpkFileChecksums)
            .resultList.map { entity ->
                entity as CpkDbChangeLogAuditEntity
                entity.toDto()
            }
    }

    /**
     * Converts a data transport object to an entity.
     */
    private fun CpkDbChangeLogAuditEntity.toDto(): CpkDbChangeLogAudit {
        return CpkDbChangeLogAudit(
            id,
            CpkDbChangeLog(
                CpkDbChangeLogIdentifier(cpkFileChecksum, filePath),
                content
            )
        )
    }

    /**
     * Converts a data transport object to an entity.
     */
    private fun CpkDbChangeLogAudit.toEntity(): CpkDbChangeLogAuditEntity {
        return CpkDbChangeLogAuditEntity(
            id = id,
            cpkFileChecksum = changeLog.id.cpkFileChecksum,
            filePath = changeLog.id.filePath,
            content = changeLog.content
        )
    }
}
