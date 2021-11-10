package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.LocalTime

/**
 * A serializer for [LocalTime] that uses a proxy object to write out the hours, minutes, seconds and the nanos.
 */
class LocalTimeSerializer : BaseProxySerializer<LocalTime, LocalTimeSerializer.LocalTimeProxy>() {
    override val type: Class<LocalTime> get() = LocalTime::class.java
    override val proxyType: Class<LocalTimeProxy> get() = LocalTimeProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: LocalTime): LocalTimeProxy = LocalTimeProxy(
        obj.hour.toByte(),
        obj.minute.toByte(),
        obj.second.toByte(),
        obj.nano
    )

    override fun fromProxy(proxy: LocalTimeProxy): LocalTime = LocalTime.of(
        proxy.hour.toInt(),
        proxy.minute.toInt(),
        proxy.second.toInt(),
        proxy.nano
    )

    data class LocalTimeProxy(val hour: Byte, val minute: Byte, val second: Byte, val nano: Int)
}
