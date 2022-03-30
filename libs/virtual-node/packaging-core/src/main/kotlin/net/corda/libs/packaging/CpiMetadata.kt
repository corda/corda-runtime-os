package net.corda.libs.packaging

import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

data class CpiMetadata(
    val cpiId: CpiIdentifier,
    val fileChecksum: SecureHash,
    val cpksMetadata: Collection<CpkMetadata>,
    val groupPolicy: String?) {
    companion object {
        fun fromAvro(other: net.corda.data.packaging.CPIMetadata) = CpiMetadata(
            CpiIdentifier.fromAvro(other.id),
            SecureHash(other.hash.algorithm, other.hash.serverHash.array()),
            other.cpks.map{ CpkMetadata.fromAvro(it) },
            other.groupPolicy
        )

        // TODO - remove
        fun fromLegacy(legacyCpi: CPI): CpiMetadata {
            return CpiMetadata(
                CpiIdentifier.fromLegacy(legacyCpi.metadata.id),
                legacyCpi.metadata.hash,
                legacyCpi.cpks.map { CpkMetadata.fromLegacyCpk(it) },
                legacyCpi.metadata.groupPolicy
            )
        }
    }

    fun toAvro(): net.corda.data.packaging.CPIMetadata {
        return net.corda.data.packaging.CPIMetadata(
            cpiId.toAvro(),
            net.corda.data.crypto.SecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cpksMetadata.map { it.toAvro() },
            groupPolicy,
        )
    }
}

