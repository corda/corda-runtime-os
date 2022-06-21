package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.packaging.core.CpiIdentifier

/**
 *  The metadata associated with a CPI file.
 *
 *  The [net.corda.libs.packaging.core.CpiMetadata] class holds the
 *  MGM group id within a free-form JSON `groupPolicy` field, whereas
 *  for publishing virtual node info to kafka and the database, we
 *  explicitly publish the group id.
 */
internal data class CpiMetadataLite(
    val id: CpiIdentifier,
    val fileChecksum: String,
    val mgmGroupId: String,
    val groupPolicy: String
)
