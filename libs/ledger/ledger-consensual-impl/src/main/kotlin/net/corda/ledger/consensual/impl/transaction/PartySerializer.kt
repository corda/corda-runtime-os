package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.consensual.Party
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

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