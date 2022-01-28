package net.corda.libs.virtualnode.write.impl

import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

/** Contains stubs of methods to read and write CPIs and holding identities to and from the cluster database. */
internal class CPIRepository {
    /** Stub for reading CPI metadata from the database that returns a dummy value. */
    @Suppress("Unused_parameter", "RedundantNullableReturnType")
    internal fun getCPIMetadata(cpiIdShortHash: String): CPIMetadata? {
        val summaryHash = SecureHash.create("SHA-256:0000000000000000")
        val cpiId = CPI.Identifier.newInstance("dummy_name", "dummy_version", summaryHash)
        return CPIMetadata(cpiId, "dummy_cpi_id_short_hash", "dummy_mgm_group_id")
    }

    /** Null-returning stub for reading a holding identity from the database. */
    @Suppress("Unused_parameter")
    internal fun getHoldingIdentity(holdingIdShortHash: String): HoldingIdentity? {
        return null
    }

    /** No-op stub for writing a holding identity to the database. */
    @Suppress("Unused_parameter")
    internal fun putHoldingIdentity(x500Name: String, mgmGroupId: String) = Unit
}