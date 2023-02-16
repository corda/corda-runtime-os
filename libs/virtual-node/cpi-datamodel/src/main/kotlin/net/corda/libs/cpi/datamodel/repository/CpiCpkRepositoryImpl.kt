package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpiCpk
import net.corda.libs.cpi.datamodel.CpiCpkIdentifier
import net.corda.libs.cpi.datamodel.CpkMetadataLite
import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.CpiCpkKey
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

class CpiCpkRepositoryImpl: CpiCpkRepository {
    override fun exists(em: EntityManager, id: CpiCpkIdentifier): Boolean {
        return em.find(CpiCpkEntity::class.java, id.toEntity()) != null
    }

    override fun findById(em: EntityManager, id: CpiCpkIdentifier): CpiCpk? {
        return em.find(CpiCpkEntity::class.java, id)?.toDto()
    }

    private fun CpiCpkIdentifier.toEntity() =
        CpiCpkKey(cpiName, cpiVersion, cpiSignerSummaryHash.toString(), cpkFileChecksum.toString())

    private fun CpiCpkKey.toDto() =
        CpiCpkIdentifier(cpiName, cpiVersion, SecureHash.parse(cpiSignerSummaryHash), SecureHash.parse(cpkFileChecksum))

    private fun CpkMetadataEntity.toDtoLite() =
        CpkMetadataLite(
            CpkIdentifier(cpkName, cpkVersion, SecureHash.parse(cpkSignerSummaryHash)),
            SecureHash.parse(cpkFileChecksum),
            CpkFormatVersion.fromString(formatVersion),
            serializedMetadata
        )

    private fun CpiCpkEntity.toDto() =
        CpiCpk(id.toDto(), cpkFileName, metadata.toDtoLite())
}
