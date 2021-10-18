package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.Period

/**
 * A serializer for [Period] that uses a proxy object to write out the integer form.
 */
class PeriodSerializer : SerializationCustomSerializer<Period, PeriodSerializer.PeriodProxy> {
    override fun toProxy(obj: Period): PeriodProxy = PeriodProxy(obj.years, obj.months, obj.days)

    override fun fromProxy(proxy: PeriodProxy): Period = Period.of(proxy.years, proxy.months, proxy.days)

    data class PeriodProxy(val years: Int, val months: Int, val days: Int)
}