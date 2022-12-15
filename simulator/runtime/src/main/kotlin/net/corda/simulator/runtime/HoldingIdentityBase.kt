package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

/**
 * @property member The member to provide a holding identity for.
 * @return A holding identity.
 */
data class HoldingIdentityBase(
    override val member: MemberX500Name,
    override val groupId: String
    ) : HoldingIdentity