package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject

/**
 * A serializer for [StringBuffer].
 */
class StringBufferSerializer : BaseDirectSerializer<StringBuffer>() {
    override val type: Class<StringBuffer> get() = StringBuffer::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: ReadObject): StringBuffer {
        return StringBuffer(reader.getAs(String::class.java))
    }

    override fun writeObject(obj: StringBuffer, writer: WriteObject) {
        writer.putAsString(obj.toString())
    }
}
