package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.math.BigDecimal

/**
 * A serializer for [BigDecimal], utilising the string based helper.  [BigDecimal] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigDecimal.toString].
 */
object BigDecimalSerializer : SerializationCustomSerializer<BigDecimal, String> {
    override fun toProxy(obj: BigDecimal): String = obj.toString()
    override fun fromProxy(proxy: String): BigDecimal = BigDecimal(proxy)
}
