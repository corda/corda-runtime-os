package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.libs.cpi.datamodel.entities.CpkFileEntity
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager
import javax.persistence.NonUniqueResultException

class CpkFileRepositoryImpl: CpkFileRepository {
    override fun exists(em: EntityManager, cpkChecksum: SecureHash): Boolean {
        val query = "SELECT count(c) FROM ${CpkFileEntity::class.simpleName} c WHERE c.fileChecksum = :cpkFileChecksum"

        val entitiesFound = em.createQuery(query)
            .setParameter("cpkFileChecksum", cpkChecksum.toString())
            .singleResult as Long

        // Todo: Should this check be here? It is strange that an exists call throws an exception about too many results found
        if (entitiesFound > 1) throw NonUniqueResultException("CpkFileEntity with fileChecksum = $cpkChecksum was not unique")

        return entitiesFound > 0
    }

    override fun put(em: EntityManager, cpkFile: CpkFile) {
        em.persist(CpkFileEntity(cpkFile.fileChecksum.toString(), cpkFile.data))
    }

    override fun findById(em: EntityManager, fileChecksums: List<SecureHash>): List<CpkFile> {
        return em.createQuery(
            "FROM ${CpkFileEntity::class.java.simpleName} f WHERE f.fileChecksum IN :ids",
            CpkFileEntity::class.java
        )
            .setParameter("ids", fileChecksums.map { it.toString() })
            .resultList.map { it.toDto() }
    }

    override fun findById(em: EntityManager, fileChecksum: SecureHash): CpkFile {
        return em.createQuery(
            "FROM ${CpkFileEntity::class.java.simpleName} WHERE fileChecksum = :checksum",
            CpkFileEntity::class.java
        )
            .setParameter("checksum", fileChecksum.toString())
            .singleResult.toDto()
    }

    override fun findByIdNotIn(em: EntityManager, fileChecksums: List<SecureHash>): List<CpkFile> {
        return em.createQuery(
            "FROM ${CpkFileEntity::class.java.simpleName} cpk " +
                    "WHERE cpk.fileChecksum NOT IN (:checksums)",
            CpkFileEntity::class.java
        )
            .setParameter(
                "checksums",
                fileChecksums.map { it.toString() }.ifEmpty { "null" })
            .resultList.map { it.toDto() }
    }

    private fun CpkFileEntity.toDto(): CpkFile {
        return CpkFile(SecureHash.parse(fileChecksum), data)
    }
}