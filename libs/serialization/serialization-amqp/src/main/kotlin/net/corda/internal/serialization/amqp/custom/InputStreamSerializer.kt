package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A serializer that writes out the content of an input stream as bytes and deserializes into a [ByteArrayInputStream].
 */
object InputStreamSerializer : SerializationCustomSerializer<InputStream, ByteArray> {
    override fun toProxy(obj: InputStream): ByteArray = obj.readAllBytes()
    override fun fromProxy(proxy: ByteArray) : InputStream = proxy.inputStream()
}