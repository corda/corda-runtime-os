package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.YearMonth

/**
 * A serializer for [YearMonth] that uses a proxy object to write out the integer form.
 */
class YearMonthSerializer : SerializationCustomSerializer<YearMonth, YearMonthSerializer.YearMonthProxy> {
    override fun toProxy(obj: YearMonth): YearMonthProxy = YearMonthProxy(obj.year, obj.monthValue.toByte())

    override fun fromProxy(proxy: YearMonthProxy): YearMonth = YearMonth.of(proxy.year, proxy.month.toInt())

    data class YearMonthProxy(val year: Int, val month: Byte)
}