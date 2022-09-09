package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

data class HoldingIdentityBase(override val member: MemberX500Name) : HoldingIdentity