package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.math.BigInteger

/**
 * A serializer for [BigInteger], utilising the string based helper.  [BigInteger] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigInteger.toString].
 */
object BigIntegerSerializer : SerializationCustomSerializer<BigInteger, String> {
    override fun toProxy(obj: BigInteger): String = obj.toString()
    override fun fromProxy(proxy: String): BigInteger = BigInteger(proxy)
}