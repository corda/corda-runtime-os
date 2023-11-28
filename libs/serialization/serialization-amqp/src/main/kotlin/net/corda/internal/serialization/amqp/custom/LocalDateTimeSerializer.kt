package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A serializer for [LocalDateTime] that uses a proxy object to write out the date and time.
 */
class LocalDateTimeSerializer : BaseProxySerializer<LocalDateTime, LocalDateTimeSerializer.LocalDateTimeProxy>() {
    override val type: Class<LocalDateTime> get() = LocalDateTime::class.java
    override val proxyType: Class<LocalDateTimeProxy> get() = LocalDateTimeProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: LocalDateTime): LocalDateTimeProxy =
        LocalDateTimeProxy(obj.toLocalDate(), obj.toLocalTime())

    override fun fromProxy(proxy: LocalDateTimeProxy): LocalDateTime =
        LocalDateTime.of(proxy.date, proxy.time)

    data class LocalDateTimeProxy(val date: LocalDate, val time: LocalTime)
}
