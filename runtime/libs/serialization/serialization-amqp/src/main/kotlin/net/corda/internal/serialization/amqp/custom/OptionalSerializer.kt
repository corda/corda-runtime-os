package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.util.Optional

/**
 * A serializer for [Optional] that uses a proxy object to write out the value stored in the optional or [Optional.EMPTY].
 */
class OptionalSerializer : BaseProxySerializer<Optional<*>, OptionalSerializer.OptionalProxy>() {
    override val type: Class<Optional<*>> get() = Optional::class.java
    override val proxyType: Class<OptionalProxy> get() = OptionalProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: Optional<*>): OptionalProxy {
        return OptionalProxy(obj.orElse(null))
    }

    override fun fromProxy(proxy: OptionalProxy): Optional<*> {
        return Optional.ofNullable(proxy.item)
    }

    data class OptionalProxy(val item: Any?)
}
