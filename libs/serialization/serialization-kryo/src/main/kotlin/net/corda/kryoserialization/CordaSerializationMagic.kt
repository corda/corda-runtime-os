package net.corda.kryoserialization

import net.corda.utilities.copyBytes
import net.corda.v5.base.types.OpaqueBytes

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteArray): ByteArray? {
        val seq = of(data)
        return if (seq.slice(start = 0, end = size) == bufferView) seq.slice(size).copyBytes() else null
    }
}
