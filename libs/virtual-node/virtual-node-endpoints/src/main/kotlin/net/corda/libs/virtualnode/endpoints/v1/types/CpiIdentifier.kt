package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.data.packaging.CpiIdentifier as CpiIdentifierAvro

/** The long identifier for a CPI. */
data class CpiIdentifier(val cpiName: String, val cpiVersion: String, val signerSummaryHash: String?) {
    companion object {
        fun fromAvro(cpiId: CpiIdentifierAvro) =
            CpiIdentifier(cpiId.name, cpiId.version, cpiId.signerSummaryHash?.toString())
    }
}