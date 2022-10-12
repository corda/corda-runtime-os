package net.corda.membership.lib.notary

import net.corda.v5.base.types.MemberX500Name

data class MemberNotaryDetails(
    val serviceName: MemberX500Name,
    val servicePlugin: String?,
    val keys: Collection<MemberNotaryKey>
)
