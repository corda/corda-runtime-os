package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import java.math.BigDecimal

/**
 * A serializer for [BigDecimal], utilising the string based helper.  [BigDecimal] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigDecimal.toString].
 */
class BigDecimalSerializer : BaseDirectSerializer<BigDecimal>() {
    override val type: Class<BigDecimal> get() = BigDecimal::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: ReadObject): BigDecimal {
        return BigDecimal(reader.getAs(String::class.java))
    }

    override fun writeObject(obj: BigDecimal, writer: WriteObject) {
        writer.putAsString(obj.toString())
    }
}
