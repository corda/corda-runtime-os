package net.cordacon.example.rollcall.utils

import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo

@Suspendable
fun findStudents(memberLookup: MemberLookup): List<MemberInfo> {
    val myInfo = memberLookup.myInfo()
    return memberLookup.lookup()
        .minus(myInfo)
        .filter { it.name.organization == myInfo.name.organization }
        .sortedBy { it.name }
}

val MemberX500Name.rollCallName: String
    get() {
        return requireNotNull(this.commonName) { "Students and teacher need a common name for the RollCall to work"}
    }