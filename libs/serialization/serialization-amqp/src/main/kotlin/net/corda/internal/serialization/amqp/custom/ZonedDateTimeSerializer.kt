package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * A serializer for [ZonedDateTime] that uses a proxy object to write out the date, time, offset and zone.
 */
class ZonedDateTimeSerializer : BaseProxySerializer<ZonedDateTime, ZonedDateTimeSerializer.ZonedDateTimeProxy>() {
    override val type: Class<ZonedDateTime> get() = ZonedDateTime::class.java
    override val proxyType: Class<ZonedDateTimeProxy> get() = ZonedDateTimeProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: ZonedDateTime): ZonedDateTimeProxy =
        ZonedDateTimeProxy(obj.toLocalDateTime(), obj.offset.id, obj.zone)

    override fun fromProxy(proxy: ZonedDateTimeProxy): ZonedDateTime =
        ZonedDateTime.ofStrict(proxy.dateTime, ZoneOffset.of(proxy.offset), proxy.zone)

    data class ZonedDateTimeProxy(val dateTime: LocalDateTime, val offset: String, val zone: ZoneId)
}
