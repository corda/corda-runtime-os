package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A serializer that writes out the content of an input stream as bytes
 * and deserializes into a [ByteArrayInputStream].
 */
class InputStreamSerializer : BaseDirectSerializer<InputStream>() {
    override val type: Class<InputStream> get() = InputStream::class.java
    override val withInheritance: Boolean get() = true
    override val revealSubclasses: Boolean get() = true

    override fun writeObject(obj: InputStream, writer: WriteObject) {
        writer.putAsBytes(obj.readAllBytes())
    }

    override fun readObject(reader: ReadObject): InputStream {
        return reader.getAsBytes().inputStream()
    }
}
