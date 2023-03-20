package net.corda.libs.cpi.datamodel.repository

import net.corda.crypto.core.parseSecureHash
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.entities.internal.CpkDbChangeLogAuditEntity
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

class CpkDbChangeLogAuditRepositoryImpl: CpkDbChangeLogAuditRepository {
    override fun put(em: EntityManager, changeLogAudit: CpkDbChangeLogAudit) {
        em.persist(changeLogAudit.toEntity())
    }

    override fun findById(em: EntityManager, id: String): CpkDbChangeLogAudit {
        val entity = em.find(CpkDbChangeLogAuditEntity::class.java, id)
        return entity.toDto()
    }

    override fun findByFileChecksums(em: EntityManager, cpkFileChecksums: Collection<SecureHash>): List<CpkDbChangeLogAudit> {
        return em.createQuery("FROM ${CpkDbChangeLogAuditEntity::class.java.simpleName} where cpkFileChecksum IN :checksums")
            .setParameter("checksums", cpkFileChecksums.map { it.toString() })
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
                CpkDbChangeLogIdentifier(parseSecureHash(cpkFileChecksum), filePath),
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
            cpkFileChecksum = changeLog.id.cpkFileChecksum.toString(),
            filePath = changeLog.id.filePath,
            content = changeLog.content
        )
    }
}
