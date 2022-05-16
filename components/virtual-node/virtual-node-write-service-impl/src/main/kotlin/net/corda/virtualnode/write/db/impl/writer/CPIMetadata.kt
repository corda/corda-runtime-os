package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.packaging.Cpi

/** The metadata associated with a CPI file. */
internal data class CPIMetadata(val id: Cpi.Identifier, val fileChecksum: String, val mgmGroupId: String)