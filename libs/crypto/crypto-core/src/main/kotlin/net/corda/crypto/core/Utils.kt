package net.corda.crypto.core
import net.corda.data.crypto.SecureHash as AvroSecureHash
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

fun SecureHash.toAvro(): AvroSecureHash =
    AvroSecureHash(this.algorithm, ByteBuffer.wrap(bytes))

fun AvroSecureHash.toCorda(): SecureHash =
    SecureHash(this.algorithm, this.bytes.array())

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

fun Long.toByteArray(): ByteArray {
    return byteArrayOf(
        (this shr 56).toByte(),
        (this shr 48).toByte(),
        (this shr 40).toByte(),
        (this shr 32).toByte(),
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        this.toByte()
    )
}