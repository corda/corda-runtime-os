package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.util.BitSet

/**
 * A serializer that writes out a [BitSet] as an integer number of bits, plus the necessary number of bytes to encode that
 * many bits.
 */
class BitSetSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<BitSet, BitSetSerializer.BitSetProxy>(
    BitSet::class.java,
    BitSetProxy::class.java,
    factory,
    withInheritance = true
) {
    override fun toProxy(obj: BitSet, context: SerializationContext): BitSetProxy = BitSetProxy(obj.toByteArray())
    override fun fromProxy(proxy: BitSetProxy): BitSet = BitSet.valueOf(proxy.bytes)

    data class BitSetProxy(val bytes: ByteArray) {
        override fun hashCode() = bytes.contentHashCode()
        override fun equals(other: Any?): Boolean {
            return this === other || (other is BitSetProxy && bytes.contentEquals(other.bytes))
        }
    }
}
