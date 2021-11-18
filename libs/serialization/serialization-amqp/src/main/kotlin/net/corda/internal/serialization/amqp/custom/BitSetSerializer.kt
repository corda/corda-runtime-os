package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.util.BitSet

/**
 * A serializer that writes out a [BitSet] as an integer number of bits, plus the necessary number of bytes to encode that
 * many bits.
 */
class BitSetSerializer : BaseProxySerializer<BitSet, BitSetSerializer.BitSetProxy>() {
    override val type: Class<BitSet> get() = BitSet::class.java
    override val proxyType: Class<BitSetProxy> get() = BitSetProxy::class.java
    override val withInheritance: Boolean get() = true

    override fun toProxy(obj: BitSet): BitSetProxy = BitSetProxy(obj.toByteArray())
    override fun fromProxy(proxy: BitSetProxy): BitSet = BitSet.valueOf(proxy.bytes)

    data class BitSetProxy(val bytes: ByteArray) {
        override fun hashCode() = bytes.contentHashCode()
        override fun equals(other: Any?): Boolean {
            return this === other || (other is BitSetProxy && bytes.contentEquals(other.bytes))
        }
    }
}
