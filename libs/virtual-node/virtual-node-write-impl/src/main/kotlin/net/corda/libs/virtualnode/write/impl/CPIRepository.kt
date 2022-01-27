package net.corda.libs.virtualnode.write.impl

import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

// TODO - Joel - Describe. Explain is temporary.
internal class CPIRepository {
    // TODO - Joel - Describe.
    // TODO - Shift from returning a dummy value to retrieving the CPI from the database.
    @Suppress("Unused_parameter", "RedundantNullableReturnType")
    internal fun getCPIMetadata(cpiIdShortHash: String): CPIMetadata? {
        val cpiId = CPIIdentifier("dummy_name", "dummy_version", SecureHash.create("SHA-256:0000000000000000"))
        return CPIMetadata(cpiId, "dummy_cpi_id_short_hash", "dummy_mgm_group_id")
    }

    // TODO - Joel - Describe.
    // TODO - Shift from returning a dummy value to retrieving the CPI from the database.
    @Suppress("Unused_parameter", "RedundantNullableReturnType")
    internal fun getHoldingIdentity(cpiIdShortHash: String): HoldingIdentity? {
        return HoldingIdentity("dummy_x500_name", "dummy_mgm_group_id")
    }

    // TODO - Joel - Describe.
    @Suppress("Unused_parameter")
    internal fun putHoldingIdentity(x500Name: String, mgmGroupId: String): HoldingIdentity {
        return HoldingIdentity("dummy_x500_name", "dummy_mgm_group_id")
    }
}