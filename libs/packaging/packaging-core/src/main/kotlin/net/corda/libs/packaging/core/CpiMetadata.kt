package net.corda.libs.packaging.core

import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

data class CpiMetadata(
    val cpiId: CpiIdentifier,
    val fileChecksum: SecureHash,
    val cpksMetadata: Collection<CpkMetadata>,
    val groupPolicy: String?,
    val version: Int = -1) {
    companion object {
        fun fromAvro(other: CpiMetadataAvro) = CpiMetadata(
            CpiIdentifier.fromAvro(other.id),
            SecureHash(other.hash.algorithm, other.hash.serverHash.array()),
            other.cpks.map{ CpkMetadata.fromAvro(it) },
            other.groupPolicy,
            other.version
        )
    }

    fun toAvro(): CpiMetadataAvro {
        return CpiMetadataAvro(
            cpiId.toAvro(),
            net.corda.data.crypto.SecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cpksMetadata.map { it.toAvro() },
            groupPolicy,
            version
        )
    }
}

