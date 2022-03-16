package net.corda.p2p.linkmanager

interface MembershipGroupListener {
    fun memberAdded(memberInfo: LinkManagerInternalTypes.MemberInfo)
}
