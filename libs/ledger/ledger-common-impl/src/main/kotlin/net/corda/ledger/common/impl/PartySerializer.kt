package net.corda.ledger.common.impl

import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
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

    override val proxyType: Class<PartyProxy>
        get() = PartyProxy::class.java

    override val type: Class<Party>
        get() = Party::class.java

    override val withInheritance: Boolean
        get() = true

    override fun toProxy(obj: Party): PartyProxy {
        return PartyProxy(obj.name.toString(), obj.owningKey)
    }

    override fun fromProxy(proxy: PartyProxy): Party {
        val name = MemberX500Name.Companion.parse(proxy.name)
        return PartyImpl(name, proxy.owningKey)
    }

    data class PartyProxy(val name: String, val owningKey: PublicKey)
}
