package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetDateTime] that uses a proxy object to write out the date and zone offset.
 */
class OffsetDateTimeSerializer : BaseProxySerializer<OffsetDateTime, OffsetDateTimeSerializer.OffsetDateTimeProxy>() {
    override val type: Class<OffsetDateTime> get() = OffsetDateTime::class.java
    override val proxyType: Class<OffsetDateTimeProxy> get() = OffsetDateTimeProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: OffsetDateTime): OffsetDateTimeProxy =
        OffsetDateTimeProxy(obj.toLocalDateTime(), obj.offset.id)

    override fun fromProxy(proxy: OffsetDateTimeProxy): OffsetDateTime =
        OffsetDateTime.of(proxy.dateTime, ZoneOffset.of(proxy.offset))

    data class OffsetDateTimeProxy(val dateTime: LocalDateTime, val offset: String)
}
