package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * A serializer for [ZonedDateTime] that uses a proxy object to write out the date, time, offset and zone.
 */
class ZonedDateTimeSerializer(
        factory: SerializerFactory
) : CustomSerializer.Proxy<ZonedDateTime, ZonedDateTimeSerializer.ZonedDateTimeProxy>(
                ZonedDateTime::class.java,
                ZonedDateTimeProxy::class.java,
                factory
) {

    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(
            LocalDateTimeSerializer(factory),
            ZoneIdSerializer(factory)
    )

    override fun toProxy(obj: ZonedDateTime): ZonedDateTimeProxy = ZonedDateTimeProxy(obj.toLocalDateTime(), obj.offset, obj.zone)

    override fun fromProxy(proxy: ZonedDateTimeProxy): ZonedDateTime = ZonedDateTime.ofInstant(
            proxy.dateTime,
            proxy.offset,
            proxy.zone
    )

    data class ZonedDateTimeProxy(val dateTime: LocalDateTime, val offset: ZoneOffset, val zone: ZoneId)
}