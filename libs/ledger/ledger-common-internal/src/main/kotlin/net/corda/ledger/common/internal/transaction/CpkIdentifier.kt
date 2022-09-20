package net.corda.ledger.common.internal.transaction

import net.corda.v5.base.annotations.CordaSerializable

//TODO(CORE-5940: This came mainly from [net.corda.libs.cpiupload.endpoints.v1.CpkIdentifier] Clarify their relationship.)
@CordaSerializable
data class CpkIdentifier(
    val name : String,
    val version : String,
    val signerSummaryHash : String?
)