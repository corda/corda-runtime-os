package net.corda.membership.lib.notary

import net.corda.v5.base.types.MemberX500Name

data class MemberNotaryDetails(
    val serviceName: MemberX500Name,
    val serviceProtocol: String?,
    val serviceProtocolVersions: Collection<Int>,
    val keys: Collection<MemberNotaryKey>,
    val backchainRequired: Boolean
)
