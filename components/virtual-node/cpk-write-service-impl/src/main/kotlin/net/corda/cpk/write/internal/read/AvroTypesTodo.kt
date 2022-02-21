package net.corda.cpk.write.internal.read

import net.corda.cpk.write.types.CpkChunk
import net.corda.cpk.write.types.CpkChunkId
import java.nio.ByteBuffer

// TODO properly define those in corda-api
object AvroTypesTodo {
    // "CPK file" topic
    // Key
    data class CpkChunkIdAvro(
        val cpkChecksum: net.corda.data.crypto.SecureHash,
        val partNumber: Int
    )

    // Value - also add here cpkchunk id ? So that values can be identified without keys as well?
    @Suppress("warnings")
    data class CpkChunkAvro(
        val cpkChunkIdAvro: CpkChunkIdAvro,
        val bytes: ByteArray
    )
}

fun net.corda.v5.crypto.SecureHash.toAvro(): net.corda.data.crypto.SecureHash =
    net.corda.data.crypto.SecureHash(this.algorithm, ByteBuffer.wrap(bytes))

fun net.corda.data.crypto.SecureHash.toCorda(): net.corda.v5.crypto.SecureHash =
    net.corda.v5.crypto.SecureHash(this.algorithm, this.serverHash.array())

fun AvroTypesTodo.CpkChunkIdAvro.toCorda(): CpkChunkId =
    CpkChunkId(this.cpkChecksum.toCorda(), this.partNumber)

fun AvroTypesTodo.CpkChunkAvro.toCorda(): CpkChunk =
    CpkChunk(this.cpkChunkIdAvro.toCorda(), this.bytes)



