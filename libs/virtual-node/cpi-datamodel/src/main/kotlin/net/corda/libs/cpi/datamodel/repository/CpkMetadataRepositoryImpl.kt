package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkMetadataLite
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

class CpkMetadataRepositoryImpl: CpkMetadataRepository {

    override fun findById(em: EntityManager, cpkFileChecksums: List<SecureHash>): List<CpkMetadataLite> {
        return em.createQuery(
            "FROM ${CpkMetadataEntity::class.java.simpleName} cpk " +
                    "WHERE cpk.cpkFileChecksum IN :cpkFileChecksums",
            CpkMetadataEntity::class.java
        )
            .setParameter("cpkFileChecksums", cpkFileChecksums.map { it.toString() })
            .resultList.map { it.toDtoLite() }
    }

    private fun CpkMetadataEntity.toDtoLite() =
        CpkMetadataLite(
            CpkIdentifier(
                cpkName,
                cpkVersion,
                SecureHash.parse(cpkSignerSummaryHash)
            ),
            SecureHash.parse(cpkFileChecksum),
            CpkFormatVersion.fromString(formatVersion),
            serializedMetadata
        )

}