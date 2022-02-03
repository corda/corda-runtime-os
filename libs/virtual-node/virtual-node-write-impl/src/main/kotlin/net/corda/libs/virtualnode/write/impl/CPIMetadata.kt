package net.corda.libs.virtualnode.write.impl

import net.corda.packaging.CPI

/** The metadata associated with a CPI file. */
internal data class CPIMetadata(val id: CPI.Identifier, val idShortHash: String, val mgmGroupId: String)