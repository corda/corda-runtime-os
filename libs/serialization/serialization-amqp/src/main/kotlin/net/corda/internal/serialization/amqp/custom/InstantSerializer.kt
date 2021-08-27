package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.time.Instant

/**
 * A serializer for [Instant] that uses a proxy object to write out the seconds since the epoch and the nanos.
 */
class InstantSerializer(
        factory: SerializerFactory
) : CustomSerializer.Proxy<Instant, InstantSerializer.InstantProxy>(
        Instant::class.java,
        InstantProxy::class.java,
        factory
) {
    override fun toProxy(obj: Instant): InstantProxy = InstantProxy(obj.epochSecond, obj.nano)

    override fun fromProxy(proxy: InstantProxy): Instant = Instant.ofEpochSecond(proxy.epochSeconds, proxy.nanos.toLong())

    data class InstantProxy(val epochSeconds: Long, val nanos: Int)
}