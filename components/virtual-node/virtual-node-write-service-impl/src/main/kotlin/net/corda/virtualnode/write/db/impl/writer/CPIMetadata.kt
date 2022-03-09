package net.corda.virtualnode.write.db.impl.writer

import net.corda.packaging.CPI

/** The metadata associated with a CPI file. */
internal data class CPIMetadata(val id: CPI.Identifier, val fileChecksum: String, val mgmGroupId: String)