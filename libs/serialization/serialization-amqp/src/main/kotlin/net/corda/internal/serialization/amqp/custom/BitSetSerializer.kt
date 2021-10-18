package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.util.BitSet

/**
 * A serializer that writes out a [BitSet] as an integer number of bits, plus the necessary number of bytes to encode that
 * many bits.
 */
class BitSetSerializer : SerializationCustomSerializer<BitSet, BitSetSerializer.BitSetProxy> {
    override fun toProxy(obj: BitSet): BitSetProxy = BitSetProxy(obj.toByteArray())
    override fun fromProxy(proxy: BitSetProxy): BitSet = BitSet.valueOf(proxy.bytes)
    data class BitSetProxy(val bytes: ByteArray)
}