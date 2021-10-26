package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.util.Optional

/**
 * A serializer for [Optional] that uses a proxy object to write out the value stored in the optional or [Optional.EMPTY].
 */
class OptionalSerializer : SerializationCustomSerializer<Optional<*>, OptionalSerializer.OptionalProxy> {

    override fun toProxy(obj: Optional<*>): OptionalProxy = OptionalProxy(obj.orElse(null))
    override fun fromProxy(proxy: OptionalProxy): Optional<*> = Optional.ofNullable(proxy.item)

    data class OptionalProxy(val item: Any?)
}