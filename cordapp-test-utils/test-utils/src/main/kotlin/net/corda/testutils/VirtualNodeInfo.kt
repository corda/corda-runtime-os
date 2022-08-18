package net.corda.testutils

import net.corda.v5.base.types.MemberX500Name

data class VirtualNodeInfo(val holdingIdentity: HoldingIdentity) {

    val member : MemberX500Name = holdingIdentity.member
}
