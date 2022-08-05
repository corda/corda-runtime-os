package net.corda.libs.cpiupload.endpoints.v1

data class CpiIdentifier(val cpiName: String, val cpiVersion: String, val signerSummaryHash: String?) {
    companion object {
        fun fromAvro(cpiId: net.corda.data.packaging.CpiIdentifier) =
            CpiIdentifier(cpiId.name, cpiId.version, cpiId.signerSummaryHash?.toString())
    }
}