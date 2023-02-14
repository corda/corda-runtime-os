package net.corda.virtualnode.write.db.impl.writer

import javax.persistence.EntityManager

internal interface CpiEntityRepository {

    fun getCpiMetadataByChecksum(cpiFileChecksum: String): CpiMetadataLite?

    fun getCPIMetadataByNameAndVersion(name: String, version: String): CpiMetadataLite?

    fun getCPIMetadataByNameAndVersion(
        em: EntityManager, name: String, version: String, signerSummaryHash: String
    ): CpiMetadataLite?
}