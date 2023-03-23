package net.corda.internal.serialization

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SerializedBytes

@CordaSerializable
class SerializedBytesImpl<T>(
    bytes: ByteArray
) : SerializedBytes<T>, OpaqueBytes(bytes)

fun <T> SerializedBytes<T>.unwrap(): SerializedBytesImpl<T> {
    return (this as? SerializedBytesImpl<T>)
        ?: throw IllegalArgumentException(
            "User defined subtypes of ${SerializedBytes::class.java.simpleName} are not permitted."
        )
}
