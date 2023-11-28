package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.Duration

/**
 * A serializer for [Duration] that uses a proxy object to write out the seconds and the nanos.
 */
class DurationSerializer : BaseProxySerializer<Duration, DurationSerializer.DurationProxy>() {
    override val type: Class<Duration> get() = Duration::class.java
    override val proxyType: Class<DurationProxy> get() = DurationProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: Duration): DurationProxy = DurationProxy(obj.seconds, obj.nano)

    override fun fromProxy(proxy: DurationProxy): Duration = Duration.ofSeconds(proxy.seconds, proxy.nanos.toLong())

    data class DurationProxy(val seconds: Long, val nanos: Int)
}
