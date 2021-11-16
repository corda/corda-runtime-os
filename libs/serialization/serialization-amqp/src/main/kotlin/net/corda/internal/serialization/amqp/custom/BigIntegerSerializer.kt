package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import java.math.BigInteger

/**
 * A serializer for [BigInteger], utilising the string based helper.  [BigInteger] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigInteger.toString].
 */
class BigIntegerSerializer : BaseDirectSerializer<BigInteger>() {
    override val type: Class<BigInteger> get() = BigInteger::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: ReadObject): BigInteger {
        return BigInteger(reader.getAs(String::class.java))
    }

    override fun writeObject(obj: BigInteger, writer: WriteObject) {
        writer.putAsString(obj.toString())
    }
}
