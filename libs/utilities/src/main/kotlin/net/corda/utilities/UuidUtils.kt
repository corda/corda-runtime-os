package net.corda.utilities

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Converts UUID straight to bytes instead of converting uuid.ToString().ToByteArray()
 * which dilutes the randomness if only part of the uuid is taken later on.
 */
fun UUID.convertToBytes(): ByteArray {
    val bb = ByteBuffer.wrap(ByteArray(16))
    bb.putLong(this.mostSignificantBits)
    bb.putLong(this.leastSignificantBits)
    return bb.array()
}
