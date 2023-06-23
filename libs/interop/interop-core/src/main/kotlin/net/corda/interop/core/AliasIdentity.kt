package net.corda.interop.core

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.types.MemberX500Name
import java.util.UUID

data class AliasIdentity(
    val realHoldingIdentityShortHash : ShortHash,
    val aliasShortHash : ShortHash,
    val x500Name : MemberX500Name,
    val groupId : UUID,
    val hostingVnode : String
)