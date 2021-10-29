package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.ZoneId

/**
 * A serializer for [ZoneId] that uses a proxy object to write out the string form.
 */
class ZoneIdSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<ZoneId, ZoneIdSerializer.ZoneIdProxy>(
    ZoneId::class.java,
    ZoneIdProxy::class.java,
    factory,
    withInheritance = true
) {
    override val revealSubclassesInSchema: Boolean
        get() = true

    override fun toProxy(obj: ZoneId, context: SerializationContext) = ZoneIdProxy(obj.id)
    override fun fromProxy(proxy: ZoneIdProxy): ZoneId = ZoneId.of(proxy.id)

    data class ZoneIdProxy(val id: String)
}
