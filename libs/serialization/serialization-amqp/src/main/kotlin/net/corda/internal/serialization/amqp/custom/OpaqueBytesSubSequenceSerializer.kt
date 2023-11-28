package net.corda.internal.serialization.amqp.custom

import net.corda.base.internal.OpaqueBytes
import net.corda.base.internal.OpaqueBytesSubSequence
import net.corda.serialization.BaseProxySerializer

/**
 * A serializer for [OpaqueBytesSubSequence] that uses a proxy object to write out only content included into sequence
 * to save on network bandwidth
 * Uses [OpaqueBytes] as a proxy
 */
class OpaqueBytesSubSequenceSerializer : BaseProxySerializer<OpaqueBytesSubSequence, OpaqueBytes>() {
    override val type: Class<OpaqueBytesSubSequence> get() = OpaqueBytesSubSequence::class.java
    override val proxyType: Class<OpaqueBytes> get() = OpaqueBytes::class.java
    override val withInheritance: Boolean get() = true

    override fun toProxy(obj: OpaqueBytesSubSequence): OpaqueBytes = OpaqueBytes(obj.copyBytes())
    override fun fromProxy(proxy: OpaqueBytes): OpaqueBytesSubSequence = OpaqueBytesSubSequence(proxy.getBytes(), proxy.offset, proxy.size)
}
