package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.YearMonth

/**
 * A serializer for [YearMonth] that uses a proxy object to write out the integer form.
 */
class YearMonthSerializer : BaseProxySerializer<YearMonth, YearMonthSerializer.YearMonthProxy>() {
    override val type: Class<YearMonth> get() = YearMonth::class.java
    override val proxyType: Class<YearMonthProxy> get() = YearMonthProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: YearMonth): YearMonthProxy = YearMonthProxy(obj.year, obj.monthValue.toByte())

    override fun fromProxy(proxy: YearMonthProxy): YearMonth = YearMonth.of(proxy.year, proxy.month.toInt())

    data class YearMonthProxy(val year: Int, val month: Byte)
}
