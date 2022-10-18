package net.corda.ledger.common.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

@CordaSerializable
data class CordaPackageSummary(
    val name: String,
    val version: String,
    val signerSummaryHash: String?,
    val fileChecksum: String,
) {

    companion object {
        fun from(data: Any?): CordaPackageSummary {
            return when (data) {
                is CordaPackageSummary -> data
                else -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val packageSummary = data as Map<String, Any?>

                        CordaPackageSummary(
                            packageSummary["name"] as String,
                            packageSummary["version"] as String,
                            packageSummary["signerSummaryHash"] as? String,
                            packageSummary["fileChecksum"] as String
                        )
                    } catch (e: Exception) {
                        throw CordaRuntimeException(
                            "Expected Corda package metadata but found [$data]."
                        )
                    }
                }
            }
        }
    }
}