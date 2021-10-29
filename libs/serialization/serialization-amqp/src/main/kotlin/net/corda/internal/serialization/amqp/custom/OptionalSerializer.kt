package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.util.Optional

/**
 * A serializer for [Optional] that uses a proxy object to write out the value stored in the optional or [Optional.EMPTY].
 */
class OptionalSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<Optional<*>, OptionalSerializer.OptionalProxy>(
    Optional::class.java,
    OptionalProxy::class.java,
    factory,
    withInheritance = false
) {

    override fun toProxy(obj: Optional<*>, context: SerializationContext): OptionalProxy {
        return OptionalProxy(obj.orElse(null))
    }

    override fun fromProxy(proxy: OptionalProxy): Optional<*> {
        return Optional.ofNullable(proxy.item)
    }

    data class OptionalProxy(val item: Any?)
}
