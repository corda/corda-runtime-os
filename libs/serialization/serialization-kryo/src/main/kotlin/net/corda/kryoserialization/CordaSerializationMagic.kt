package net.corda.kryoserialization

import net.corda.utilities.copyBytes
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.types.OpaqueBytes

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteSequence): ByteArray? {
        return if (data.slice(start = 0, end = size) == bufferView) data.slice(size).copyBytes() else null
    }
}
