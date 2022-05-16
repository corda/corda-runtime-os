package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.packaging.core.CpiIdentifier

/** The metadata associated with a CPI file. */
internal data class CPIMetadata(val id: CpiIdentifier, val fileChecksum: String, val mgmGroupId: String)