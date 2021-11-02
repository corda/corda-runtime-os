package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetTime] that uses a proxy object to write out the time and zone offset.
 */
class OffsetTimeSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<OffsetTime, OffsetTimeSerializer.OffsetTimeProxy>(
    OffsetTime::class.java,
    OffsetTimeProxy::class.java,
    factory,
    withInheritance = false
) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(
        LocalTimeSerializer(factory),
    )

    override fun toProxy(obj: OffsetTime): OffsetTimeProxy
        = OffsetTimeProxy(obj.toLocalTime(), obj.offset.id)

    override fun fromProxy(proxy: OffsetTimeProxy): OffsetTime
        = OffsetTime.of(proxy.time, ZoneOffset.of(proxy.offset))

    data class OffsetTimeProxy(val time: LocalTime, val offset: String)
}
