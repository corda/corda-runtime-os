package net.corda.utilities

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Converts UUID straight to byte array instead of converting uuid.toString().toByteArray()
 * which dilutes the randomness if only part of the uuid is taken later on.
 */
fun UUID.toByteArray(): ByteArray {
    val bb = ByteBuffer.wrap(ByteArray(32))
    bb.putLong(this.mostSignificantBits)
    bb.putLong(this.leastSignificantBits)
    return bb.array()
}
