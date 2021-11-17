package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.ZoneId

/**
 * A serializer for [ZoneId] that uses a proxy object to write out the string form.
 */
class ZoneIdSerializer : BaseProxySerializer<ZoneId, ZoneIdSerializer.ZoneIdProxy>() {
    override val type: Class<ZoneId> get() = ZoneId::class.java
    override val proxyType: Class<ZoneIdProxy> get() = ZoneIdProxy::class.java
    override val withInheritance: Boolean get() = true
    override val revealSubclasses: Boolean get() = true

    override fun toProxy(obj: ZoneId) = ZoneIdProxy(obj.id)
    override fun fromProxy(proxy: ZoneIdProxy): ZoneId = ZoneId.of(proxy.id)

    data class ZoneIdProxy(val id: String)
}
