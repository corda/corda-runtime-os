package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.packaging.core.CpiMetadata
import java.util.stream.Stream
import javax.persistence.EntityManager

/**
 * Interface for CRUD operations for cpi metadata
 */
interface CpiMetadataRepository {
    /**
     * Find all cpi metadata.
     */
    fun findAll(em: EntityManager): Stream<CpiMetadata>
}

