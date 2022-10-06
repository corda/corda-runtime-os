package net.corda.ledger.common.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

//TODO(CORE-5940: This came mainly from [net.corda.libs.cpiupload.endpoints.v1.CpkIdentifier] Clarify their relationship.)
@CordaSerializable
data class CpkMetadata(
    val name: String,
    val version: String,
    val signerSummaryHash: String?,
    val fileChecksum: String
)
