package net.corda.simulator.runtime.messaging

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

/**
 * Simple implementation of the MemberInfo class. Note most methods are unimplemented. This class is also
 * immutable (as with real Corda); if keys are added for a particular member then this will need to be re-retrieved from
 * [net.corda.v5.application.membership.MemberLookup].
 *
 * @param name The name of the member.
 * @param ledgerKeys Ledger keys generated for this member.
 */
data class BaseMemberInfo(
    override val name: MemberX500Name,
    override val ledgerKeys: List<PublicKey> = listOf()
) : MemberInfo {

    override val isActive: Boolean = true
    override val memberProvidedContext: MemberContext
        get() { TODO("Not yet implemented") }
    override val mgmProvidedContext: MGMContext
        get() { TODO("Not yet implemented") }
    override val platformVersion: Int
        get() { TODO("Not yet implemented") }
    override val serial: Long
        get() { TODO("Not yet implemented") }
    override val sessionInitiationKey: PublicKey
        get() { TODO("Not yet implemented") }

}
