package net.corda.membership.registration.management.impl.handler

import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import net.corda.data.identity.HoldingIdentity as DataHoldingIdentity
import net.corda.virtualnode.HoldingIdentity as CordaHoldingIdentity

internal class MemberTypeChecker(
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) {
    fun isMgm(identity: DataHoldingIdentity) = isMgm(identity.toCorda())

    fun isMgm(identity: CordaHoldingIdentity) =
        getMgmMemberInfo(identity) != null

    fun getMgmMemberInfo(identity: CordaHoldingIdentity): MemberInfo? {
        val mgmMemberName = identity.x500Name
        val memberInfo = membershipGroupReaderProvider
            .getGroupReader(identity)
            .lookup(mgmMemberName)
        return if (memberInfo?.isMgm == true) {
            memberInfo
        } else {
            null
        }
    }
}
