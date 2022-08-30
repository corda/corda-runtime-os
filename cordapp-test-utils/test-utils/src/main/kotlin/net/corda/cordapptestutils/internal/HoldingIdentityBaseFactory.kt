package net.corda.cordapptestutils.internal

import net.corda.cordapptestutils.HoldingIdentity
import net.corda.cordapptestutils.factories.HoldingIdentityFactory
import net.corda.v5.base.types.MemberX500Name

class HoldingIdentityBaseFactory : HoldingIdentityFactory {
    override fun create(memberX500Name: MemberX500Name): HoldingIdentity {
        return HoldingIdentityBase(memberX500Name)
    }
}