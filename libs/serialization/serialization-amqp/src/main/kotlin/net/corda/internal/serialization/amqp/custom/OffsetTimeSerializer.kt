package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetTime] that uses a proxy object to write out the time and zone offset.
 */
class OffsetTimeSerializer : BaseProxySerializer<OffsetTime, OffsetTimeSerializer.OffsetTimeProxy>() {
    override val type: Class<OffsetTime> get() = OffsetTime::class.java
    override val proxyType: Class<OffsetTimeProxy> get() = OffsetTimeProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: OffsetTime): OffsetTimeProxy =
        OffsetTimeProxy(obj.toLocalTime(), obj.offset.id)

    override fun fromProxy(proxy: OffsetTimeProxy): OffsetTime =
        OffsetTime.of(proxy.time, ZoneOffset.of(proxy.offset))

    data class OffsetTimeProxy(val time: LocalTime, val offset: String)
}
