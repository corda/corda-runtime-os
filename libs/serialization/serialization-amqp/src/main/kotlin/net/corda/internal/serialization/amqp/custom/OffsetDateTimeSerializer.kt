package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetDateTime] that uses a proxy object to write out the date and zone offset.
 */
class OffsetDateTimeSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<OffsetDateTime, OffsetDateTimeSerializer.OffsetDateTimeProxy>(
    OffsetDateTime::class.java,
    OffsetDateTimeProxy::class.java,
    factory,
    withInheritance = false
) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(
        LocalDateTimeSerializer(factory),
    )

    override fun toProxy(obj: OffsetDateTime, context: SerializationContext): OffsetDateTimeProxy
        = OffsetDateTimeProxy(obj.toLocalDateTime(), obj.offset.id)

    override fun fromProxy(proxy: OffsetDateTimeProxy): OffsetDateTime
        = OffsetDateTime.of(proxy.dateTime, ZoneOffset.of(proxy.offset))

    data class OffsetDateTimeProxy(val dateTime: LocalDateTime, val offset: String)
}
