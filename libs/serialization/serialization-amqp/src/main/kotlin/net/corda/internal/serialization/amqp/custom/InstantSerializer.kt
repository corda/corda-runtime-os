package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.time.Instant

/**
 * A serializer for [Instant] that uses a proxy object to write out the seconds since the epoch and the nanos.
 */
class InstantSerializer : BaseProxySerializer<Instant, InstantSerializer.InstantProxy>() {
    override val type: Class<Instant> get() = Instant::class.java
    override val proxyType: Class<InstantProxy> get() = InstantProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: Instant): InstantProxy =
        InstantProxy(obj.epochSecond, obj.nano)

    override fun fromProxy(proxy: InstantProxy): Instant =
        Instant.ofEpochSecond(proxy.epochSeconds, proxy.nanos.toLong())

    data class InstantProxy(val epochSeconds: Long, val nanos: Int)
}
