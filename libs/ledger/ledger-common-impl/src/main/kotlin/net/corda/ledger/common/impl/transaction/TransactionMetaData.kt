package net.corda.ledger.common.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

//TODO(CORE-5940: guarantee its serialization is deterministic)
@CordaSerializable
class TransactionMetaData(private val properties: Map<String, Any>) {

    operator fun get(key: String): Any? = properties[key]

    val entries: Set<Map.Entry<String, Any>>
        get() = properties.entries

    companion object {
        const val LEDGER_MODEL_KEY = "ledgerModel"
        const val LEDGER_VERSION_KEY = "ledgerVersion"
        const val DIGEST_SETTINGS_KEY = "digestSettings"
        const val PLATFORM_VERSION_KEY = "platformVersion"
        const val CPI_METADATA_KEY = "cpiMetadata"

        const val CPI_NAME_KEY = "cpiName"
        const val CPI_VERSION_KEY = "cpiVersion"
        const val CPI_CHECKSUM_KEY = "cpiChecksum"
        const val CPI_SIGNER_SUMMARY_HASH_KEY = "cpiSignerSummaryHash"
        const val CPK_METADATA_KEY = "cpkMetadata"
        const val CPK_NAME_KEY = "cpkName"
        const val CPK_VERSION_KEY = "cpkVersion"
        const val CPK_CHECKSUM_KEY = "cpkChecksum"
        const val CPK_SIGNER_SUMMARY_HASH_KEY = "cpkSignerSummaryHash"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TransactionMetaData) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    fun getLedgerModel(): String = this[LEDGER_MODEL_KEY].toString()

    fun getLedgerVersion(): String = this[LEDGER_VERSION_KEY].toString()

    fun getCpiMetadata(): Map<String, Any> {
        val data = this[CPI_METADATA_KEY] ?: return emptyMap()
        try {
            @Suppress("UNCHECKED_CAST")
            return data as Map<String, Any>
        } catch (e: Exception) {
            throw CordaRuntimeException(
                "Transaction metadata representation error: CPI metadata must be a map but found ${data.javaClass}")
        }
    }

    fun getDigestSettings(): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this[DIGEST_SETTINGS_KEY] as Map<String, Any>
    }
}