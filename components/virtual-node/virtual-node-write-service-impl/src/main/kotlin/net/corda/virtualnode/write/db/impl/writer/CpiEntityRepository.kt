package net.corda.virtualnode.write.db.impl.writer

import javax.persistence.EntityManager
import net.corda.libs.packaging.core.CpiIdentifier

internal interface CpiEntityRepository {

    fun getCpiMetadataByChecksum(cpiFileChecksum: String): CpiMetadataLite?

    fun getCPIMetadataByNameAndVersion(name: String, version: String): CpiMetadataLite?

    fun getCPIMetadataById(em: EntityManager, id: CpiIdentifier): CpiMetadataLite?
}

