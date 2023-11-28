package net.corda.libs.cpiupload.endpoints.v1

/**
 * NOTE:
 * This class is visible to end-users via JSON serialization,
 * so the variable names are significant and should be consistent
 * anywhere they are used as parameters.
 */
/**
 * CPI Identifier
 *
 * @param cpiName Name of the CPI.
 * @param cpiVersion Version of the CPI.
 * @param signerSummaryHash Hash of all signers of the CPI.
 */
data class CpiIdentifier(val cpiName: String, val cpiVersion: String, val signerSummaryHash: String?) {
    companion object {
        fun fromAvro(cpiId: net.corda.data.packaging.CpiIdentifier) =
            CpiIdentifier(cpiId.name, cpiId.version, cpiId.signerSummaryHash?.toString())
    }
}
