package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.LocalDate

/**
 * A serializer for [LocalDate] that uses a proxy object to write out the year, month and day.
 */
class LocalDateSerializer : BaseProxySerializer<LocalDate, LocalDateSerializer.LocalDateProxy>() {
    override val type: Class<LocalDate> get() = LocalDate::class.java
    override val proxyType: Class<LocalDateProxy> get() = LocalDateProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: LocalDate): LocalDateProxy =
        LocalDateProxy(obj.year, obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: LocalDateProxy): LocalDate =
        LocalDate.of(proxy.year, proxy.month.toInt(), proxy.day.toInt())

    data class LocalDateProxy(val year: Int, val month: Byte, val day: Byte)
}
