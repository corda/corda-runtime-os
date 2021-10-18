package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer

/**
 * A serializer for [StringBuffer].
 */
object StringBufferSerializer : SerializationCustomSerializer<StringBuffer, String> {
    override fun toProxy(obj: StringBuffer): String = obj.toString()
    override fun fromProxy(proxy: String): StringBuffer = StringBuffer(proxy)
}