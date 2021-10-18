package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.MonthDay

/**
 * A serializer for [MonthDay] that uses a proxy object to write out the integer form.
 */
class MonthDaySerializer : SerializationCustomSerializer<MonthDay, MonthDaySerializer.MonthDayProxy> {
    override fun toProxy(obj: MonthDay): MonthDayProxy = MonthDayProxy(obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: MonthDayProxy): MonthDay = MonthDay.of(proxy.month.toInt(), proxy.day.toInt())

    data class MonthDayProxy(val month: Byte, val day: Byte)
}