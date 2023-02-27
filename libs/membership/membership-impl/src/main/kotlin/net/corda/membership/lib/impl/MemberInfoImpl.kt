package net.corda.membership.lib.impl

import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

class MemberInfoImpl(
    private val memberProvidedContext: MemberContext,
    private val mgmProvidedContext: MGMContext,
) : MemberInfo {

    init {
        require(endpoints.isNotEmpty()) { "Node must have at least one address." }
        require(platformVersion > 0) { "Platform version must be at least 1." }
        require(softwareVersion.isNotEmpty()) { "Node software version must not be blank." }
    }

    override fun getMemberProvidedContext() = memberProvidedContext
    override fun getMgmProvidedContext() = mgmProvidedContext
    override fun getName(): MemberX500Name = memberProvidedContext.parse(PARTY_NAME)
    override fun getSessionInitiationKey(): PublicKey = memberProvidedContext.parse(PARTY_SESSION_KEY)
    override fun getLedgerKeys(): List<PublicKey> = memberProvidedContext.parseList(LEDGER_KEYS)
    override fun getPlatformVersion(): Int = memberProvidedContext.parse(PLATFORM_VERSION)
    override fun getSerial(): Long = mgmProvidedContext.parse(SERIAL)
    override fun isActive(): Boolean = mgmProvidedContext.parse(STATUS, String::class.java) == MEMBER_STATUS_ACTIVE

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MemberInfoImpl) return false
        if (this === other) return true
        return memberProvidedContext == other.memberProvidedContext && mgmProvidedContext == other.mgmProvidedContext
    }

    override fun hashCode(): Int {
        var result = memberProvidedContext.hashCode()
        result = 31 * result + mgmProvidedContext.hashCode()
        return result
    }
}
