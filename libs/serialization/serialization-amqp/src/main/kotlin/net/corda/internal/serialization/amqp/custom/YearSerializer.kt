package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.Year

/**
 * A serializer for [Year] that uses a proxy object to write out the integer form.
 */
class YearSerializer : BaseProxySerializer<Year, YearSerializer.YearProxy>() {
    override val type: Class<Year> get() = Year::class.java
    override val proxyType: Class<YearProxy> get() = YearProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: Year): YearProxy = YearProxy(obj.value)

    override fun fromProxy(proxy: YearProxy): Year = Year.of(proxy.year)

    data class YearProxy(val year: Int)
}
