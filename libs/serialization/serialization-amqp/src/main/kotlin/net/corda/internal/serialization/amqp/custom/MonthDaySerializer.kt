package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.MonthDay

/**
 * A serializer for [MonthDay] that uses a proxy object to write out the integer form.
 */
class MonthDaySerializer : BaseProxySerializer<MonthDay, MonthDaySerializer.MonthDayProxy>() {
    override val type: Class<MonthDay> get() = MonthDay::class.java
    override val proxyType: Class<MonthDayProxy> get() = MonthDayProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: MonthDay): MonthDayProxy =
        MonthDayProxy(obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: MonthDayProxy): MonthDay =
        MonthDay.of(proxy.month.toInt(), proxy.day.toInt())

    data class MonthDayProxy(val month: Byte, val day: Byte)
}
