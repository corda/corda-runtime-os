package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.data.packaging.CPIIdentifier as CPIIdentifierAvro

/** The long identifier for a CPI. */
data class CPIIdentifier(val cpiName: String, val cpiVersion: String, val signerSummaryHash: String) {
    companion object {
        fun fromAvro(cpiId: CPIIdentifierAvro) =
            CPIIdentifier(cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString())
    }
}