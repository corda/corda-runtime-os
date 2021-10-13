package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetTime] that uses a proxy object to write out the time and zone offset.
 */
class OffsetTimeSerializer : SerializationCustomSerializer<OffsetTime, OffsetTimeSerializer.OffsetTimeProxy> {
    override fun toProxy(obj: OffsetTime): OffsetTimeProxy = OffsetTimeProxy(obj.toLocalTime(), obj.offset.id)

    override fun fromProxy(proxy: OffsetTimeProxy): OffsetTime = OffsetTime.of(proxy.time, ZoneOffset.of(proxy.offset))

    data class OffsetTimeProxy(val time: LocalTime, val offset: String)
}