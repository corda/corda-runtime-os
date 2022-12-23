package net.corda.virtualnode.write.db.impl.writer.dto

data class HoldingIdentityLite(
    val holdingIdentityX500Name: String,
    val groupId: String,
    val hsmConnectionId: String?,
)