package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.ZoneId

/**
 * A serializer for [ZoneId] that uses a proxy object to write out the string form.
 */
class ZoneIdSerializer : SerializationCustomSerializer<ZoneId, ZoneIdSerializer.ZoneIdProxy> {

    override fun toProxy(obj: ZoneId): ZoneIdProxy = ZoneIdProxy(obj.id)

    override fun fromProxy(proxy: ZoneIdProxy): ZoneId = ZoneId.of(proxy.id)

    data class ZoneIdProxy(val id: String)
}