package net.corda.libs.packaging.core

import net.corda.data.packaging.CpiMetadata as AvroCpiMetadata
import net.corda.libs.packaging.Cpi
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.time.Instant

data class CpiMetadata(
    val cpiId: CpiIdentifier,
    val fileChecksum: SecureHash,
    val cpksMetadata: Collection<CpkMetadata>,
    val groupPolicy: String?,
    val version: Int = -1,
    val timestamp: Instant) {
    companion object {
        fun fromAvro(other: AvroCpiMetadata) = CpiMetadata(
            CpiIdentifier.fromAvro(other.id),
            SecureHash(other.hash.algorithm, other.hash.serverHash.array()),
            other.cpks.map{ CpkMetadata.fromAvro(it) },
            other.groupPolicy,
            other.version,
            other.timestamp
        )

        // TODO - remove
        fun fromLegacy(legacyCpi: Cpi, timestamp: Instant = Instant.now()): CpiMetadata {
            return CpiMetadata(
                CpiIdentifier.fromLegacy(legacyCpi.metadata.id),
                legacyCpi.metadata.hash,
                legacyCpi.cpks.map { CpkMetadata.fromLegacyCpk(it) },
                legacyCpi.metadata.groupPolicy,
                -1,
                timestamp
            )
        }
    }

    fun toAvro(): AvroCpiMetadata {
        return AvroCpiMetadata(
            cpiId.toAvro(),
            net.corda.data.crypto.SecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cpksMetadata.map { it.toAvro() },
            groupPolicy,
            version,
            timestamp
        )
    }
}

