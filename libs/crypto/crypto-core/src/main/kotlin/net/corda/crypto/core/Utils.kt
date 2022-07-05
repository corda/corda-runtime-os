package net.corda.crypto.core

fun concatByteArrays(vararg concat: ByteArray): ByteArray {
    if (concat.isEmpty()) {
        return ByteArray(0)
    }
    val length = concat.sumOf { it.size }
    val output = ByteArray(length)
    var offset = 0
    for (segment in concat) {
        val segmentSize = segment.size
        System.arraycopy(segment, 0, output, offset, segmentSize)
        offset += segmentSize
    }
    return output
}

fun Int.toByteArray(): ByteArray {
    return byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte())
}