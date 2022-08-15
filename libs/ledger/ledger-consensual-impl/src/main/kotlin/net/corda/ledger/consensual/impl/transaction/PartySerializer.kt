package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.ledger.consensual.Party
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [InternalCustomSerializer::class])
class PartySerializer @Activate constructor() : BaseProxySerializer<Party, PartyImpl>() {
    override fun toProxy(obj: Party): PartyImpl = PartyImpl(obj.name, obj.owningKey)

    override fun fromProxy(proxy: PartyImpl): Party = proxy

    override val proxyType: Class<PartyImpl>
        get() = PartyImpl::class.java
    override val type: Class<Party>
        get() = Party::class.java
    override val withInheritance: Boolean
        get() = true
}