package net.corda.ledger.common.impl.transactions

import net.corda.v5.base.annotations.CordaSerializable

//TODO: Mainly from [net.corda.libs.cpiupload.endpoints.v1.CpkIdentifier] Clarify their relationship
@CordaSerializable
data class CpkIdentifier(
    val name : String,
    val version : String,
    val signerSummaryHash : String?
)