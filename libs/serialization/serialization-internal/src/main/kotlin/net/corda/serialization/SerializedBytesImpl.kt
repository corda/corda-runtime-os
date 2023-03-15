package net.corda.serialization

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SerializedBytes

@CordaSerializable
class SerializedBytesImpl<T>(
    bytes: ByteArray
) : SerializedBytes<T>, OpaqueBytes(bytes) {
    override fun getSummary(): String {
        return "SerializedBytes(size = $size)";
    }
}
