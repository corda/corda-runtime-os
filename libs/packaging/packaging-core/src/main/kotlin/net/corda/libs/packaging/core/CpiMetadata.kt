package net.corda.libs.packaging.core

import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

data class CpiMetadata(
    val cpiId: CpiIdentifier,
    val fileChecksum: SecureHash,
    val cpksMetadata: Collection<CpkMetadata>,
    val groupPolicy: String?,
    val version: Int = -1,
    val timestamp: Instant,
    val isDeleted: Boolean = false,
    val groupId: String = "" ) {
    companion object {
        fun fromAvro(other: CpiMetadataAvro) = CpiMetadata(
            CpiIdentifier.fromAvro(other.id),
            SecureHash(other.hash.algorithm, other.hash.bytes.array()),
            other.cpks.map { CpkMetadata.fromAvro(it) },
            other.groupPolicy,
            other.version,
            other.timestamp
        )
    }

    fun contractCpksMetadata(): Collection<CpkMetadata> =
        cpksMetadata.filter(CpkMetadata::isContractCpk)

    fun toAvro(): CpiMetadataAvro {
        return CpiMetadataAvro(
            cpiId.toAvro(),
            net.corda.data.crypto.SecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cpksMetadata.map { it.toAvro() },
            groupPolicy,
            version,
            timestamp
        )
    }
}

