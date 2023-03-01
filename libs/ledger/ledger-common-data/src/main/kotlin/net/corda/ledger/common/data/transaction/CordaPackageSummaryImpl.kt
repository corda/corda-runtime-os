package net.corda.ledger.common.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

@CordaSerializable
data class CordaPackageSummaryImpl(
    private val name: String,
    private val version: String,
    private val signerSummaryHash: String?,
    private val fileChecksum: String,
) : CordaPackageSummary {

    companion object {
        fun from(data: Any?): CordaPackageSummaryImpl {
            return when (data) {
                is CordaPackageSummaryImpl -> data
                else -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val packageSummary = data as Map<String, Any?>

                        CordaPackageSummaryImpl(
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

    override fun getName(): String {
        return name
    }

    override fun getVersion(): String {
        return version
    }

    override fun getSignerSummaryHash(): String? {
        return signerSummaryHash
    }

    override fun getFileChecksum(): String {
        return fileChecksum
    }
}
