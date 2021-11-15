package net.corda.v5.serialization

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes

/**
 * A type safe wrapper around a byte array that contains a serialised object.
 */
@Suppress("unused")
@CordaSerializable
class SerializedBytes<T : Any>(bytes: ByteArray) : OpaqueBytes(bytes) {
    val summary: String get() = "SerializedBytes(size = $size)"
}
