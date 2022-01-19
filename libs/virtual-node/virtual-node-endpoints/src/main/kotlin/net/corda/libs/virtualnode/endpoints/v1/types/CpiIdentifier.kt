package net.corda.libs.virtualnode.endpoints.v1.types

/** The long identifier for a CPI. */
data class CpiIdentifier(val cpiName: String, val cpiVersion: String, val signerSummaryHash: String)