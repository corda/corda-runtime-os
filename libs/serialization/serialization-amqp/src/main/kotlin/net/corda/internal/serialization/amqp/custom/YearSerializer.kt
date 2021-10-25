package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.Year

/**
 * A serializer for [Year] that uses a proxy object to write out the integer form.
 */
class YearSerializer : SerializationCustomSerializer<Year, YearSerializer.YearProxy> {
    override fun toProxy(obj: Year): YearProxy = YearProxy(obj.value)

    override fun fromProxy(proxy: YearProxy): Year = Year.of(proxy.year)

    data class YearProxy(val year: Int)
}