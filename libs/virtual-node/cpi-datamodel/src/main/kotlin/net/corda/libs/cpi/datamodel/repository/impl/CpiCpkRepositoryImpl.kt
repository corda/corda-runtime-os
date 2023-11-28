package net.corda.libs.cpi.datamodel.repository.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.libs.cpi.datamodel.CpiCpkIdentifier
import net.corda.libs.cpi.datamodel.CpiCpkMetadata
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkKey
import net.corda.libs.cpi.datamodel.repository.CpiCpkRepository
import javax.persistence.EntityManager

internal class CpiCpkRepositoryImpl : CpiCpkRepository {
    override fun exist(em: EntityManager, cpiCpkId: CpiCpkIdentifier): Boolean {
        return em.find(
            CpiCpkEntity::class.java,
            CpiCpkKey(
                cpiCpkId.cpiName,
                cpiCpkId.cpiVersion,
                cpiCpkId.cpiSignerSummaryHash.toString(),
                cpiCpkId.cpkFileChecksum.toString()
            )
        ) != null
    }

    override fun findById(em: EntityManager, cpiCpkId: CpiCpkIdentifier): CpiCpkMetadata? {
        return em.find(
            CpiCpkEntity::class.java,
            CpiCpkKey(
                cpiCpkId.cpiName,
                cpiCpkId.cpiVersion,
                cpiCpkId.cpiSignerSummaryHash.toString(),
                cpiCpkId.cpkFileChecksum.toString()
            )
        ).toDto()
    }

    private fun CpiCpkEntity.toDto() =
        CpiCpkMetadata(
            CpiCpkIdentifier(
                id.cpiName,
                id.cpiVersion,
                parseSecureHash(id.cpiSignerSummaryHash),
                parseSecureHash(id.cpkFileChecksum)
            ),
            cpkFileName,
            entityVersion
        )
}
