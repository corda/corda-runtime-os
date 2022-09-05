package net.corda.cordapptestutils.factories

import net.corda.cordapptestutils.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

interface HoldingIdentityFactory {
    fun create(memberX500Name: MemberX500Name): HoldingIdentity
}