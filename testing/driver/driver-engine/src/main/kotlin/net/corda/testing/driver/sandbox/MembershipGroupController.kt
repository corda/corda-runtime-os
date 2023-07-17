package net.corda.testing.driver.sandbox

import java.util.SortedMap
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.MemberInfo

interface MembershipGroupController : MembershipGroupReader {
    val membership: Set<MemberInfo>

    fun updateMembership(memberInfo: MemberInfo, mgmContext: SortedMap<String, String?>)
}
