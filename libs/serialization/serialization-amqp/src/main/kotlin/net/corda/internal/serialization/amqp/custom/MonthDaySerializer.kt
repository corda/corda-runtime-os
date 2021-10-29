package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.MonthDay

/**
 * A serializer for [MonthDay] that uses a proxy object to write out the integer form.
 */
class MonthDaySerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<MonthDay, MonthDaySerializer.MonthDayProxy>(
    MonthDay::class.java,
    MonthDayProxy::class.java,
    factory,
    withInheritance = false
) {
    override fun toProxy(obj: MonthDay, context: SerializationContext): MonthDayProxy
        = MonthDayProxy(obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: MonthDayProxy): MonthDay
        = MonthDay.of(proxy.month.toInt(), proxy.day.toInt())

    data class MonthDayProxy(val month: Byte, val day: Byte)
}
