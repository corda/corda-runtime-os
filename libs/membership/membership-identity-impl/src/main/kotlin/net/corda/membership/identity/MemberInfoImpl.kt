package net.corda.membership.identity

import net.corda.membership.identity.MemberInfoExtension.Companion.IDENTITY_KEYS
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.identity.MemberInfoExtension.Companion.STATUS
import net.corda.membership.identity.MemberInfoExtension.Companion.endpoints
import net.corda.membership.identity.MemberInfoExtension.Companion.softwareVersion
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.membership.identity.MGMContext
import net.corda.v5.membership.identity.MemberContext
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

class MemberInfoImpl(
    override val memberProvidedContext: MemberContext,
    override val mgmProvidedContext: MGMContext
) : MemberInfo {

    init {
        require(endpoints.isNotEmpty()) { "Node must have at least one address." }
        require(platformVersion > 0) { "Platform version must be at least 1." }
        require(softwareVersion.isNotEmpty()) { "Node software version must not be blank." }
        require(owningKey in identityKeys) { "Identity key must be in the key list." }
    }

    override val name: MemberX500Name get() = memberProvidedContext.parse(PARTY_NAME)

    override val owningKey: PublicKey get() = memberProvidedContext.parse(PARTY_OWNING_KEY)

    override val identityKeys: List<PublicKey> get() = memberProvidedContext.parseList(IDENTITY_KEYS)

    override val platformVersion: Int get() = memberProvidedContext.parse(PLATFORM_VERSION)

    override val serial: Long get() = memberProvidedContext.parse(SERIAL)

    override val isActive: Boolean get() = mgmProvidedContext.parse(STATUS, String::class.java) == MEMBER_STATUS_ACTIVE

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