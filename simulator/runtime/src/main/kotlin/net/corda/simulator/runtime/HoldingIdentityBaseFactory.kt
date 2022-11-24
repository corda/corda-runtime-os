package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.factories.HoldingIdentityFactory
import net.corda.v5.base.types.MemberX500Name

/**
 * @see [HoldingIdentityFactory] for details.
 */
class HoldingIdentityBaseFactory : HoldingIdentityFactory {
    override fun create(memberX500Name: MemberX500Name): HoldingIdentity {
        return net.corda.simulator.runtime.HoldingIdentityBase(memberX500Name)
    }
}