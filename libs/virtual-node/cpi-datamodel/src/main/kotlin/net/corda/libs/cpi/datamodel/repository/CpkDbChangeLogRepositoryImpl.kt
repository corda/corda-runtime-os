package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpkDbChangeLogKey
import net.corda.libs.packaging.core.CpiIdentifier
import javax.persistence.EntityManager

class CpkDbChangeLogRepositoryImpl: CpkDbChangeLogRepository {
    override fun put(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog) {
        em.persist(cpkDbChangeLog.toEntity())
    }

    override fun update(em: EntityManager, cpkDbChangeLog: CpkDbChangeLog) {
        em.merge(cpkDbChangeLog.toEntity())
    }

    override fun findByFileChecksum(
        em: EntityManager,
        cpkFileChecksums: Set<String>
    ): List<CpkDbChangeLog> {
        return cpkFileChecksums.chunked(100).map { batch ->
            em.createQuery(
                "FROM ${CpkDbChangeLogEntity::class.simpleName}" +
                        " WHERE id.cpkFileChecksum IN :cpkFileChecksums",
                CpkDbChangeLogEntity::class.java
            ).setParameter("cpkFileChecksums", batch)
                .resultList.map { it.toDto() }
        }.flatten()
    }

    override fun findByContent(em: EntityManager, content: String): List<CpkDbChangeLog> {
        return em.createQuery(
            "FROM ${CpkDbChangeLogEntity::class.simpleName} where content = :value",
            CpkDbChangeLogEntity::class.java
        ).setParameter("value", content)
            .resultList.map { it.toDto() }
    }

    override fun findByCpiId(em: EntityManager, cpiIdentifier: CpiIdentifier): List<CpkDbChangeLog> {
        return em.createQuery(
            "SELECT changelog " +
                    "FROM ${CpkDbChangeLogEntity::class.simpleName} AS changelog INNER JOIN " +
                    "${CpiCpkEntity::class.simpleName} AS cpiCpk " +
                    "ON changelog.id.cpkFileChecksum = cpiCpk.id.cpkFileChecksum " +
                    "WHERE cpiCpk.id.cpiName = :name AND " +
                    "      cpiCpk.id.cpiVersion = :version AND " +
                    "      cpiCpk.id.cpiSignerSummaryHash = :signerSummaryHash AND " +
                    "      changelog.isDeleted = FALSE " +
                    "ORDER BY changelog.insertTimestamp DESC",
            CpkDbChangeLogEntity::class.java
        )
            .setParameter("name", cpiIdentifier.name)
            .setParameter("version", cpiIdentifier.version)
            .setParameter("signerSummaryHash", cpiIdentifier.signerSummaryHash.toString())
            .resultList.map { it.toDto() }
    }

    override fun findById(em: EntityManager, cpkChangeLogIdentifier: CpkDbChangeLogIdentifier): CpkDbChangeLog {
        return em.find(
            CpkDbChangeLogEntity::class.java,
            CpkDbChangeLogKey(cpkChangeLogIdentifier.cpkFileChecksum, cpkChangeLogIdentifier.filePath)
        ).toDto()
    }

    /**
     * Converts a data transport object to an entity.
     */
    private fun CpkDbChangeLog.toEntity() =
        CpkDbChangeLogEntity(id.toEntity(), content)

    private fun CpkDbChangeLogIdentifier.toEntity() =
        CpkDbChangeLogKey(cpkFileChecksum, filePath)


    /**
     * Converts an entity to a data transport object.
     */
    private fun CpkDbChangeLogEntity.toDto() =
        CpkDbChangeLog(id.toDto(), content)

    /**
     * Converts an entity to a data transport object.
     */
    private fun CpkDbChangeLogKey.toDto() =
        CpkDbChangeLogIdentifier(cpkFileChecksum, filePath)

}