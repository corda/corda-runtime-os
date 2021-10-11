package net.corda.membership.impl

import net.corda.membership.impl.MemberInfoExtension.Companion.IDENTITY_KEYS
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.impl.serialization.PublicKeyStringConverter
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.node.KeyValueStore
import net.corda.v5.application.node.MemberInfo
import net.corda.v5.base.annotations.CordaSerializable
import java.security.PublicKey

@CordaSerializable
class MemberInfoImpl(
    override val memberProvidedContext: KeyValueStore,
    override val mgmProvidedContext: KeyValueStore
) : MemberInfo {

    init {
        require(endpoints.isNotEmpty()) { "Node must have at least one address" }
        require(platformVersion > 0) { "Platform version must be at least 1" }
        require(softwareVersion.isNotEmpty()) { "Node software version must not be blank" }
        require(owningKey in identityKeys) { "Identity key must be in the key list" }
    }

    override val name: CordaX500Name get() = memberProvidedContext.parse(PARTY_NAME)

    override val owningKey: PublicKey get() = memberProvidedContext.parse(PARTY_OWNING_KEY)

    override val identityKeys: List<PublicKey> get() = readIdentityKeys()

    override val platformVersion: Int get() = memberProvidedContext.parse(PLATFORM_VERSION)

    override val serial: Long get() = memberProvidedContext.parse(SERIAL)

    override val isActive: Boolean get() = ( mgmProvidedContext.parse(STATUS) as String == MEMBER_STATUS_ACTIVE )

    private fun readIdentityKeys() = memberProvidedContext.parseList(IDENTITY_KEYS, PublicKeyStringConverter((memberProvidedContext as KeyValueStoreImpl).keyEncodingService))

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