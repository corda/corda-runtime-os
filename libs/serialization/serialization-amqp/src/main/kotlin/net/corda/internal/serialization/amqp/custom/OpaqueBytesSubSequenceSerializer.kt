package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.base.types.OpaqueBytesSubSequence

/**
 * A serializer for [OpaqueBytesSubSequence] that uses a proxy object to write out only content included into sequence
 * to save on network bandwidth
 * Uses [OpaqueBytes] as a proxy
 */
class OpaqueBytesSubSequenceSerializer : InternalCustomSerializer<OpaqueBytesSubSequence, OpaqueBytes> {
    override val type: Class<OpaqueBytesSubSequence> get() = OpaqueBytesSubSequence::class.java
    override val proxyType: Class<OpaqueBytes> get() = OpaqueBytes::class.java
    override val withInheritance: Boolean get() = true

    override fun toProxy(obj: OpaqueBytesSubSequence): OpaqueBytes
        = OpaqueBytes(obj.copyBytes())
    override fun fromProxy(proxy: OpaqueBytes): OpaqueBytesSubSequence
        = OpaqueBytesSubSequence(proxy.bytes, proxy.offset, proxy.size)
}
