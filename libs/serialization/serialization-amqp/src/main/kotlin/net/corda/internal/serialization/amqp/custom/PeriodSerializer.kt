package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.Period

/**
 * A serializer for [Period] that uses a proxy object to write out the integer form.
 */
class PeriodSerializer : BaseProxySerializer<Period, PeriodSerializer.PeriodProxy>() {
    override val type: Class<Period> get() = Period::class.java
    override val proxyType: Class<PeriodProxy> get() = PeriodProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: Period): PeriodProxy =
        PeriodProxy(obj.years, obj.months, obj.days)

    override fun fromProxy(proxy: PeriodProxy): Period =
        Period.of(proxy.years, proxy.months, proxy.days)

    data class PeriodProxy(val years: Int, val months: Int, val days: Int)
}
