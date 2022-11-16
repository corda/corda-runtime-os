package net.corda.ledger.common.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

@CordaSerializable
class TransactionMetadata(private val properties: LinkedHashMap<String, Any>) {

    operator fun get(key: String): Any? = properties[key]

    companion object {
        const val ALL_LEDGER_METADATA_COMPONENT_GROUP_ID = 0
        const val SCHEMA_PATH = "/schema/transaction-metadata.json"
        const val SCHEMA_VERSION = 1

        const val LEDGER_MODEL_KEY = "ledgerModel"
        const val LEDGER_VERSION_KEY = "ledgerVersion"
        const val TRANSACTION_SUBTYPE_KEY = "transactionSubtype"
        const val DIGEST_SETTINGS_KEY = "digestSettings"
        const val PLATFORM_VERSION_KEY = "platformVersion"
        const val CPI_METADATA_KEY = "cpiMetadata"
        const val CPK_METADATA_KEY = "cpkMetadata"
        const val SCHEMA_VERSION_KEY = "schemaVersion"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TransactionMetadata) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    fun getLedgerModel(): String = this[LEDGER_MODEL_KEY].toString()

    fun getLedgerVersion(): Int {
        val version = this[LEDGER_VERSION_KEY].toString()

        return try {
            version.toInt()
        } catch (e: NumberFormatException) {
            throw CordaRuntimeException(
                "Transaction metadata representation error: ledger version should be an integer but could not be parsed: $version")
        }
    }

    fun getTransactionSubtype(): String? = this[TRANSACTION_SUBTYPE_KEY]?.toString()

    fun getCpiMetadata(): CordaPackageSummary? = this[CPI_METADATA_KEY]?.let { CordaPackageSummary.from(it) }

    fun getCpkMetadata(): List<CordaPackageSummary> {
        return when (val data = this[CPK_METADATA_KEY]) {
            null -> emptyList()
            is List<*> -> data.map { CordaPackageSummary.from(it) }
            else -> throw CordaRuntimeException(
                "Transaction metadata representation error: expected list of Corda package metadata but found [$data]")
        }
    }

    fun getDigestSettings(): LinkedHashMap<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this[DIGEST_SETTINGS_KEY] as LinkedHashMap<String, Any>
    }

    fun getSchemaVersion(): Int {
        val version = this[SCHEMA_VERSION_KEY].toString()

        return try {
            version.toInt()
        } catch (e: NumberFormatException) {
            throw CordaRuntimeException(
                "Transaction metadata representation error: JSON schema version should be an integer but could not be parsed: $version")
        }
    }
}