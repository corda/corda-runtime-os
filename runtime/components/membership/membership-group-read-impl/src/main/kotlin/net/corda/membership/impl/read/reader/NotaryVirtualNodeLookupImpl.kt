package net.corda.membership.impl.read.reader

import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo

internal class NotaryVirtualNodeLookupImpl(
    private val membershipGroupReader: MembershipGroupReader
) : NotaryVirtualNodeLookup {
    override fun getNotaryVirtualNodes(notaryServiceName: MemberX500Name): List<MemberInfo> {
        return membershipGroupReader.lookup().filter {
            it.notaryDetails?.serviceName == notaryServiceName
        }.sortedBy {
            it.name
        }
    }
}
