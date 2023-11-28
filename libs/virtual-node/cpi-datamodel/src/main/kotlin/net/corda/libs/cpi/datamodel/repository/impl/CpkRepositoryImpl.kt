package net.corda.libs.cpi.datamodel.repository.impl

import net.corda.libs.cpi.datamodel.entities.internal.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.repository.CpkRepository
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

internal class CpkRepositoryImpl : CpkRepository {
    override fun findById(em: EntityManager, cpkFileChecksum: SecureHash): Pair<Int, CpkMetadata>? {
        val cpkMetadataEntity = em.find(CpkMetadataEntity::class.java, cpkFileChecksum.toString()) ?: return null

        return Pair(cpkMetadataEntity.entityVersion, cpkMetadataEntity.toDto())
    }

    private fun CpkMetadataEntity.toDto() =
        CpkMetadata.fromJsonAvro(serializedMetadata)
}
