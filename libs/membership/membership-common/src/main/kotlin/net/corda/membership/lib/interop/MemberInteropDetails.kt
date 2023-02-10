package net.corda.membership.lib.interop

import net.corda.v5.base.types.MemberX500Name

data class MemberInteropDetails(
    val serviceName: MemberX500Name,
    val servicePlugin: String?,
    val keys: Collection<MemberInteropKey>
)