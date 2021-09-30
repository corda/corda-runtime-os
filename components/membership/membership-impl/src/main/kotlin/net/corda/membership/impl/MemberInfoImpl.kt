package net.corda.membership.impl

import net.corda.membership.impl.MemberInfoExtension.Companion.IDENTITY_KEYS
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.softwareVersion
import net.corda.v5.application.identity.Party
import net.corda.v5.application.node.MemberContext
import net.corda.v5.application.node.MemberInfo
import net.corda.v5.application.node.readAs
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.cipher.suite.KeyEncodingService
import org.apache.commons.lang3.builder.HashCodeBuilder
import java.security.PublicKey

@CordaSerializable
class MemberInfoImpl(
    override val memberProvidedContext: MemberContext,
    override val mgmProvidedContext: MemberContext,
    private val keyEncodingService: KeyEncodingService
) : MemberInfo {

    init {
        require(endpoints.isNotEmpty()) { "Node must have at least one address" }
        require(platformVersion > 0) { "Platform version must be at least 1" }
        require(softwareVersion.isNotEmpty()) { "Node software version must not be blank" }
        require(party.owningKey in identityKeys) { "Identity key must be in the key list" }
    }

    override val party: Party get() = readParty()

    override val identityKeys: List<PublicKey> get() = readIdentityKeys()

    override val platformVersion: Int get() = memberProvidedContext.readAs(PLATFORM_VERSION)

    override val serial: Long get() = memberProvidedContext.readAs(SERIAL)

    override val isActive: Boolean get() = ( mgmProvidedContext.readAs(STATUS) as String == MEMBER_STATUS_ACTIVE )

    override fun toString(): String = "MemberInfo { \n memberProvidedContext { \n$memberProvidedContext\n} " +
            "mgmProvidedContext { \n$mgmProvidedContext \n}\n}"

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MemberInfoImpl) return false
        if (this === other) return true
        return this.memberProvidedContext == other.memberProvidedContext && this.mgmProvidedContext == other.mgmProvidedContext
    }

    override fun hashCode() = HashCodeBuilder(71, 97)
        .append(memberProvidedContext)
        .append(mgmProvidedContext)
        .toHashCode()

    private fun readParty() = PartyImpl(
        memberProvidedContext.readAs(PARTY_NAME),
        keyEncodingService.decodePublicKey(memberProvidedContext.readAs<String>(PARTY_KEY))
    )

    private fun readIdentityKeys() = memberProvidedContext.entries.filter { it.key.startsWith(IDENTITY_KEYS) }
        .map { keyEncodingService.decodePublicKey(it.value) }
}