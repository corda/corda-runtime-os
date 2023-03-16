package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.crypto.SecureHash

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
    val fileChecksum: SecureHash,
    val mgmGroupId: String,
    val groupPolicy: String
)
