package net.corda.libs.packaging

import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

/**
 * Cpi identifier
 *
 * @property name The name of the CPI, read from its manifest
 * @property version The version number of the CPI, read from its manifest
 * @property signerSummaryHash The hash of the concatenation of hashes of the public keys of the signers
 *              of the CPI, null if the CPI is unsigned
 */
data class CpiIdentifier(
    override val name : String,
    override val version : String,
    override val signerSummaryHash : SecureHash?,
) : Identifier, Comparable<CpiIdentifier> {
    companion object {
        fun fromAvro(other: net.corda.data.packaging.CPIIdentifier) = CpiIdentifier(
            other.name,
            other.version,
            other.signerSummaryHash?.let { SecureHash(it.algorithm, it.serverHash.array()) },
        )

        // TODO - remove
        fun fromLegacy(legacyId: CPI.Identifier): CpiIdentifier {
            return CpiIdentifier(legacyId.name, legacyId.version, legacyId.signerSummaryHash)
        }
    }

    override fun compareTo(other: CpiIdentifier) = identifierComparator.compare(this, other)

    fun toAvro(): net.corda.data.packaging.CPIIdentifier {
        return net.corda.data.packaging.CPIIdentifier(
            name,
            version,
            signerSummaryHash?.let { hash ->
                net.corda.data.crypto.SecureHash(
                    hash.algorithm,
                    ByteBuffer.wrap(hash.bytes)
                )
            },
        )
    }
}
