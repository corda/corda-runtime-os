package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.Instant

/**
 * A serializer for [Instant] that uses a proxy object to write out the seconds since the epoch and the nanos.
 */
class InstantSerializer : SerializationCustomSerializer<Instant, InstantSerializer.InstantProxy> {
    override fun toProxy(obj: Instant): InstantProxy = InstantProxy(obj.epochSecond, obj.nano)

    override fun fromProxy(proxy: InstantProxy): Instant = Instant.ofEpochSecond(proxy.epochSeconds, proxy.nanos.toLong())

    data class InstantProxy(val epochSeconds: Long, val nanos: Int)
}