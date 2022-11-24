package net.corda.simulator.factories

import net.corda.simulator.HoldingIdentity
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name

/**
 * Creates the holding ID which is needed to create virtual nodes using Simulator. This interface should not
 * be used directly; instead use the [net.corda.simulator.HoldingIdentity.create] methods on
 * [net.corda.simulator.HoldingIdentity] itself.
 */
@DoNotImplement
interface HoldingIdentityFactory {

    /**
     * @param memberX500Name The member for which to create a [HoldingIdentity].
     */
    fun create(memberX500Name: MemberX500Name): HoldingIdentity
}