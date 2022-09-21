package net.corda.membership.impl.registration.dynamic.handler

import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import net.corda.data.identity.HoldingIdentity as DataHoldingIdentity
import net.corda.virtualnode.HoldingIdentity as CordaHoldingIdentity

internal class MemberTypeChecker(
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) {
    fun isMgm(identity: DataHoldingIdentity) = isMgm(identity.toCorda())

    fun isMgm(identity: CordaHoldingIdentity): Boolean {
        println("QQQ in is MGM for $identity")
        val mgm = groupPolicyProvider.getGroupPolicy(identity)
        println("QQQ \t ${mgm?.mgmInfo}")
        return getMgmMemberInfo(identity) != null
    }


    fun getMgmMemberInfo(identity: CordaHoldingIdentity): MemberInfo? {
        println("QQQ In getMgmMemberInfo for ${identity.x500Name}")
        val mgmMemberName = identity.x500Name

        val memberInfo = membershipGroupReaderProvider
            .getGroupReader(identity)
            .lookup(mgmMemberName)
        println("QQQ  memberInfo -> $memberInfo")
        return if (memberInfo?.isMgm == true) {
            memberInfo.also {
                println("QQQ I am MGM!")
            }
        } else {
            null
        }
    }
}
