package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.Year

/**
 * A serializer for [Year] that uses a proxy object to write out the integer form.
 */
class YearSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<Year, YearSerializer.YearProxy>(
    Year::class.java,
    YearProxy::class.java,
    factory,
    withInheritance = false
) {
    override fun toProxy(obj: Year, context: SerializationContext): YearProxy
        = YearProxy(obj.value)

    override fun fromProxy(proxy: YearProxy): Year
        = Year.of(proxy.year)

    data class YearProxy(val year: Int)
}
