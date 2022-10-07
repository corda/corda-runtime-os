package net.corda.ledger.common.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class CpiSummary(
    val name: String,
    val version: String,
    val signerSummaryHash: String?,
    val fileChecksum: String,
    val cpks: List<CpkSummary>
)