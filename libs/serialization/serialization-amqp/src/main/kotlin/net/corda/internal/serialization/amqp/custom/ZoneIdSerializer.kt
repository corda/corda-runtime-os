package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.ZoneId

/**
 * A serializer for [ZoneId] that uses a proxy object to write out the string form.
 */
class ZoneIdSerializer : SerializationCustomSerializer<ZoneId, String> {
    override fun toProxy(obj: ZoneId): String = obj.id
    override fun fromProxy(proxy: String): ZoneId = ZoneId.of(proxy)
}
