package net.corda.libs.virtualnode.write.impl

import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import net.corda.data.crypto.SecureHash as SecureHashAvro
import net.corda.data.packaging.CPIIdentifier as CPIIdentifierAvro

/** The metadata associated with a CPI file. */
internal data class CPIMetadata(val id: CPIIdentifier, val idShortHash: String, val mgmGroupId: String)

/** The identifier of a CPI file. */
internal data class CPIIdentifier(val name: String, val version: String, val signerSummaryHash: SecureHash) {
    /** Converts the [CPIIdentifier] to its Avro representation. */
    fun toAvro(): CPIIdentifierAvro {
        val secureHashAvro = SecureHashAvro(signerSummaryHash.algorithm, ByteBuffer.wrap(signerSummaryHash.bytes))
        return net.corda.data.packaging.CPIIdentifier(name, version, secureHashAvro)
    }
}