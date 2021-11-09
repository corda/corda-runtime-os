package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.Duration

/**
 * A serializer for [Duration] that uses a proxy object to write out the seconds and the nanos.
 */
class DurationSerializer : SerializationCustomSerializer<Duration, DurationSerializer.DurationProxy> {
    override fun toProxy(obj: Duration): DurationProxy = DurationProxy(obj.seconds, obj.nano)

    override fun fromProxy(proxy: DurationProxy): Duration = Duration.ofSeconds(proxy.seconds, proxy.nanos.toLong())

    data class DurationProxy(val seconds: Long, val nanos: Int)
}