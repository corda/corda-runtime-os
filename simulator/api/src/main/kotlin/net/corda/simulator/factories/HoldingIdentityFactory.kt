package net.corda.simulator.factories

import net.corda.simulator.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

interface HoldingIdentityFactory {
    fun create(memberX500Name: MemberX500Name): HoldingIdentity
}