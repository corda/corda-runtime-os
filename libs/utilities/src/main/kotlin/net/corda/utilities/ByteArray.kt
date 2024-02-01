package net.corda.utilities

import net.corda.base.internal.ByteSequence
import net.corda.base.internal.OpaqueBytesSubSequence
import java.nio.ByteBuffer

fun ByteArray.toByteSequence() : ByteSequence {
    return OpaqueBytesSubSequence(this, 0, this.size)
}

/**
 * Extension functions to assist in translating ByteBuffers to ByteArrays and vice versa.
 * ByteBuffers are not @CordaSerializable.
 */
fun List<ByteArray>.toByteBuffers() = this.map { ByteBuffer.wrap(it) }
fun List<ByteBuffer>.toByteArrays() = this.map { it.array()}
fun Map<String, ByteArray?>.toByteBuffers() = this.mapValues { value -> value.value?.let { ByteBuffer.wrap(value.value) }  }
fun Map<String, ByteBuffer?>.toByteArrays() = this.mapValues { value -> value.value?.array() }