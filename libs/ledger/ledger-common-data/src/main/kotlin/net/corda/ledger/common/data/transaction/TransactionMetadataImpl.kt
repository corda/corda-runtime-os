package net.corda.ledger.common.data.transaction

import com.fasterxml.jackson.annotation.JsonIgnore
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

@CordaSerializable
class TransactionMetadataImpl(private val properties: Map<String, Any>) : TransactionMetadataInternal {

    operator fun get(key: String): Any? = properties[key]

    companion object {
        const val ALL_LEDGER_METADATA_COMPONENT_GROUP_ID = 0
        const val SCHEMA_VERSION = 1

        const val LEDGER_MODEL_KEY = "ledgerModel"
        const val LEDGER_VERSION_KEY = "ledgerVersion"
        const val TRANSACTION_SUBTYPE_KEY = "transactionSubtype"
        const val DIGEST_SETTINGS_KEY = "digestSettings"
        const val PLATFORM_VERSION_KEY = "platformVersion"
        const val CPI_METADATA_KEY = "cpiMetadata"
        const val CPK_METADATA_KEY = "cpkMetadata"
        const val COMPONENT_GROUPS_KEY = "componentGroups"
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        const val MEMBERSHIP_GROUP_PARAMETERS_HASH_KEY = "membershipGroupParametersHash"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TransactionMetadataImpl) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    override fun getLedgerModel(): String = this[LEDGER_MODEL_KEY].toString()

    override fun getLedgerVersion(): Int {
        val version = this[LEDGER_VERSION_KEY].toString()

        return try {
            version.toInt()
        } catch (e: NumberFormatException) {
            throw CordaRuntimeException(
                "Transaction metadata representation error: ledger version should be an integer but could not be parsed: $version"
            )
        }
    }

    override fun getTransactionSubtype(): String? = this[TRANSACTION_SUBTYPE_KEY]?.toString()

    override fun getCpiMetadata(): CordaPackageSummary? = this[CPI_METADATA_KEY]?.let { CordaPackageSummaryImpl.from(it) }

    override fun getCpkMetadata(): List<CordaPackageSummary> {
        return when (val data = this[CPK_METADATA_KEY]) {
            null -> emptyList()
            is List<*> -> data.map { CordaPackageSummaryImpl.from(it) }
            else -> throw CordaRuntimeException(
                "Transaction metadata representation error: expected list of Corda package metadata but found [$data]"
            )
        }
    }

    override fun getComponentGroups(): List<List<String>> {
        val value = this[COMPONENT_GROUPS_KEY]
        @Suppress("UNCHECKED_CAST")
        return value as? List<List<String>> ?: throw CordaRuntimeException(
            "Transaction metadata representation error: expected List<List<String>> representing the component groups but found [$value]"
        )
    }

    @JsonIgnore
    override fun getNumberOfComponentGroups(): Int {
        return getComponentGroups().size
    }

    override fun getDigestSettings(): Map<String, String> {
        @Suppress("UNCHECKED_CAST")
        return this[DIGEST_SETTINGS_KEY] as Map<String, String>
    }

    override fun getSchemaVersion(): Int {
        val version = this[SCHEMA_VERSION_KEY].toString()

        return try {
            version.toInt()
        } catch (e: NumberFormatException) {
            throw CordaRuntimeException(
                "Transaction metadata representation error: JSON schema version should be an integer but could not be parsed: $version"
            )
        }
    }

    override fun getMembershipGroupParametersHash(): String? = this[MEMBERSHIP_GROUP_PARAMETERS_HASH_KEY]?.toString()

    override fun getPlatformVersion(): Int {
        val version = this[PLATFORM_VERSION_KEY].toString()

        return try {
            version.toInt()
        } catch (e: NumberFormatException) {
            throw CordaRuntimeException(
                "Transaction metadata representation error: Platform version should be an integer but could not be parsed: $version"
            )
        }
    }
}
