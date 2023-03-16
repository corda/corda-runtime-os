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
    private val name: MemberX500Name,
    private val ledgerKeys: List<PublicKey> = listOf()
) : MemberInfo {

    override fun getName() = name
    override fun getLedgerKeys() = ledgerKeys
    override fun isActive() = true

    override fun getMemberProvidedContext(): MemberContext {
        TODO("Not yet implemented")
    }

    override fun getMgmProvidedContext(): MGMContext {
        TODO("Not yet implemented")
    }

    override fun getPlatformVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getSerial(): Long {
        TODO("Not yet implemented")
    }

    override fun getSessionInitiationKey(): PublicKey {
        TODO("Not yet implemented")
    }
}
