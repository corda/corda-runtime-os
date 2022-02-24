package net.corda.cpk.write.impl.services.kafka

import net.corda.cpk.write.impl.CpkChunkId
import java.nio.ByteBuffer

// TODO properly define those in corda-api
object AvroTypesTodo {
    // "CPK file" topic
    // Key
    data class CpkChunkIdAvro(
        val cpkChecksum: net.corda.data.crypto.SecureHash,
        val partNumber: Int/*,
        val publisherId: String // TODO: so that the receiver can only trust chunks from the same publisher
        */
    )
}

fun net.corda.v5.crypto.SecureHash.toAvro(): net.corda.data.crypto.SecureHash =
    net.corda.data.crypto.SecureHash(this.algorithm, ByteBuffer.wrap(bytes))

fun net.corda.data.crypto.SecureHash.toCorda(): net.corda.v5.crypto.SecureHash =
    net.corda.v5.crypto.SecureHash(this.algorithm, this.serverHash.array())

fun AvroTypesTodo.CpkChunkIdAvro.toCorda(): CpkChunkId =
    CpkChunkId(this.cpkChecksum.toCorda(), this.partNumber)

fun CpkChunkId.toAvro(): AvroTypesTodo.CpkChunkIdAvro =
    AvroTypesTodo.CpkChunkIdAvro(this.cpkChecksum.toAvro(), this.partNumber)
