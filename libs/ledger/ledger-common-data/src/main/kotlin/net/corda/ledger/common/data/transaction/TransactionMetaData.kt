package net.corda.ledger.common.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

@CordaSerializable
class TransactionMetaData(private val properties: LinkedHashMap<String, Any>) {

    operator fun get(key: String): Any? = properties[key]

    companion object {
        const val LEDGER_MODEL_KEY = "ledgerModel"
        const val LEDGER_VERSION_KEY = "ledgerVersion"
        const val TRANSACTION_SUBTYPE_KEY = "transactionSubtype"
        const val DIGEST_SETTINGS_KEY = "digestSettings"
        const val PLATFORM_VERSION_KEY = "platformVersion"
        const val CPI_METADATA_KEY = "cpiMetadata"
        const val CPK_METADATA_KEY = "cpkMetadata"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TransactionMetaData) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    fun getLedgerModel(): String = this[LEDGER_MODEL_KEY].toString()

    fun getLedgerVersion(): Int {
        val version = this[LEDGER_VERSION_KEY].toString()

        try {
            return Integer.parseInt(version)
        } catch (e: NumberFormatException) {
            throw CordaRuntimeException(
            "Transaction metadata representation error: ledger version should be an integer but could not be parsed: $version")
        }
    }

    fun getTransactionSubtype(): String = this[TRANSACTION_SUBTYPE_KEY].toString()

    fun getCpiMetadata(): CordaPackageSummary? = this[CPI_METADATA_KEY]?.let { CordaPackageSummary.from(it) }

    fun getCpkMetadata(): List<CordaPackageSummary> {
        return when (val data = this[CPK_METADATA_KEY]) {
            null -> emptyList()
            is List<*> -> data.map { CordaPackageSummary.from(it) }
            else -> throw CordaRuntimeException(
                "Transaction metadata representation error: expected list of Corda package metadata but found [$data]")
        }
    }

    fun getDigestSettings(): LinkedHashMap<String, Any>{
        @Suppress("UNCHECKED_CAST")
        return this[DIGEST_SETTINGS_KEY] as LinkedHashMap<String, Any>
    }
}