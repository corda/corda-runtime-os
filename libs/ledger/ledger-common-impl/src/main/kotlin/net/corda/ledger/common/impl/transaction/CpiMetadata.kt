package net.corda.ledger.common.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class CpiMetadata(
    val name: String,
    val version: String,
    val signerSummaryHash: String?,
    val fileChecksum: String,
    val cpks: List<CpkMetadata>
)