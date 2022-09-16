package net.corda.ledger.consensual.impl

import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.consensual.Party
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

/**
 * We need the PartySerializer instead of simple CordaSerializable since ConsensualStates will be implemented by
 * end-users, so they'll depend on the interface. Also, the PartyImpl class won't be visible them. So the automatic
 * serializer needs a clear path from Party to PartyImpl to work with their classes
 */
@Component(service = [InternalCustomSerializer::class])
class PartySerializer @Activate constructor() : BaseProxySerializer<Party, PartySerializer.PartyProxy>() {
    override fun toProxy(obj: Party): PartyProxy = PartyProxy(obj.name.toString(), obj.owningKey)

    override fun fromProxy(proxy: PartyProxy): Party = PartyImpl(MemberX500Name.Companion.parse(proxy.name), proxy.owningKey)

    override val proxyType: Class<PartyProxy>
        get() = PartyProxy::class.java
    override val type: Class<Party>
        get() = Party::class.java
    override val withInheritance: Boolean
        get() = true

    data class PartyProxy(val name: String, val owningKey: PublicKey)
}