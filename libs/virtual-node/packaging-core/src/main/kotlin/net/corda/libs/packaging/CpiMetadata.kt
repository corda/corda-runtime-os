package net.corda.libs.packaging

import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

data class CpiMetadata(
    val id : CpiIdentifier,
    val hash : SecureHash,
    val cpks : Collection<CpkMetadata>,
    val groupPolicy : String?) {
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
            id.toAvro(),
            net.corda.data.crypto.SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes)),
            cpks.map { it.toAvro() },
            groupPolicy,
        )
    }
}

