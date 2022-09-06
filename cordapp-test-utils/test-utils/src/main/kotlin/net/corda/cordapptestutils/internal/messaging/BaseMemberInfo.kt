package net.corda.cordapptestutils.internal.messaging

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

data class BaseMemberInfo(
    override val name: MemberX500Name,
) : MemberInfo {

    override val isActive: Boolean = true
    override val ledgerKeys: List<PublicKey>
        get() { TODO("Not yet implemented") }
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
