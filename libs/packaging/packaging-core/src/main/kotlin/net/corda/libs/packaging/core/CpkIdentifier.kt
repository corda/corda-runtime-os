package net.corda.libs.packaging.core

import net.corda.libs.packaging.core.comparator.identifierComparator
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import net.corda.data.packaging.CpkIdentifier as CpkIdentifierAvro

/**
 * Uniquely identifies a CPK archive
 *
 * @property name The Bundle-SymbolicName of the main bundle inside the CPK
 * @property version The Bundle-Version of the main bundle inside the CPK
 * @property signerSummaryHash The hash of concatenation of the sorted hashes of the public keys of the
 *              signers of the CPK, null if the CPK isn't signed
 * @constructor Create empty Cpk identifier
 */
data class CpkIdentifier(
    override val name: String,
    override val version: String,
    override val signerSummaryHash: SecureHash?,
) : Identifier, Comparable<CpkIdentifier> {
    companion object {
        fun fromAvro(other: CpkIdentifierAvro): CpkIdentifier {
            return CpkIdentifier(
                other.name,
                other.version,
                other.signerSummaryHash?.let { SecureHash(it.algorithm, it.serverHash.array()) },
            )
        }
    }

    override fun compareTo(other: CpkIdentifier) = identifierComparator.compare(this, other)

    fun toAvro(): CpkIdentifierAvro {
        return CpkIdentifierAvro(
            name,
            version,
            signerSummaryHash?.let { hash ->
                net.corda.data.crypto.SecureHash(
                    hash.algorithm,
                    ByteBuffer.wrap(hash.bytes)
                )
            }
        )
    }
}
