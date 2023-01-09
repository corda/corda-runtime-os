package net.corda.libs.packaging.core

import net.corda.libs.packaging.core.comparator.identifierComparator
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import net.corda.data.packaging.CpiIdentifier as CpiIdentifierAvro

/**
 * Cpi identifier
 *
 * @property name The name of the CPI, read from its manifest
 * @property version The version number of the CPI, read from its manifest
 * @property signerSummaryHash The hash of the concatenation of hashes of the public keys of the signers
 *              of the CPI, null if the CPI is unsigned
 */
data class CpiIdentifier(
    override val name: String,
    override val version: String,
    override val signerSummaryHash: SecureHash,
) : Identifier, Comparable<CpiIdentifier> {
    companion object {
        fun fromAvro(other: CpiIdentifierAvro) = CpiIdentifier(
            other.name,
            other.version,
            other.signerSummaryHash.let { SecureHash(it.algorithm, it.serverHash.array()) },
        )
    }

    override fun compareTo(other: CpiIdentifier) = identifierComparator.compare(this, other)

    fun toAvro(): CpiIdentifierAvro {
        return CpiIdentifierAvro(
            name,
            version,
            net.corda.data.crypto.SecureHash(
                signerSummaryHash.algorithm,
                ByteBuffer.wrap(signerSummaryHash.bytes)
            )
        )
    }
}
