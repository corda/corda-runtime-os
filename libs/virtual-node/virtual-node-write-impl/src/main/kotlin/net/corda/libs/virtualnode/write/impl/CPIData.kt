package net.corda.libs.virtualnode.write.impl

import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

// TODO - Joel - Describe.
internal data class CPIMetadata(val id: CPIIdentifier, val idShortHash: String, val mgmGroupId: String)

// TODO - Joel - Describe.
internal data class CPIIdentifier(val name: String, val version: String, val signerSummaryHash: SecureHash) {
    // TODO - Joel - Describe.
    fun toAvro(): net.corda.data.packaging.CPIIdentifier {
        return net.corda.data.packaging.CPIIdentifier(name, version, signerSummaryHash.toAvro())
    }

    // TODO - Joel - Describe.
    private fun SecureHash.toAvro() = net.corda.data.crypto.SecureHash(algorithm, ByteBuffer.wrap(bytes))
}