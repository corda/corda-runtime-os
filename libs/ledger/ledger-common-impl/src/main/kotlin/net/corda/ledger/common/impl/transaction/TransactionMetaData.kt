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
        const val CPK_METADATA_KEY = "cpkMetadata"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TransactionMetaData) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    fun getLedgerModel(): String = this[LEDGER_MODEL_KEY].toString()

    fun getLedgerVersion(): String = this[LEDGER_VERSION_KEY].toString()

    fun getCpiMetadata(): CpiSummary? {
        return when (val data = this[CPI_METADATA_KEY]) {
            is CpiSummary -> data
            is Map<*, *> -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val cpi = data as Map<String, Any?>

                    CpiSummary(
                        cpi["name"].toString(),
                        cpi["version"].toString(),
                        cpi["signerSummaryHash"]?.toString(),
                        cpi["fileChecksum"].toString())
                } catch (e: Exception) {
                    throw CordaRuntimeException(
                        "Transaction metadata representation error: expected CPI metadata but found [$data]"
                    )
                }
            }
            null -> null
            else ->
                throw CordaRuntimeException(
                    "Transaction metadata representation error: expected CPI metadata but found [$data]"
                )
        }
    }

    fun getCpkMetadata(): List<CpkSummary> {
        return when (val data = this[CPK_METADATA_KEY]) {
            null -> emptyList()
            is List<*> -> {
                return data.map {
                    when (it) {
                        is CpkSummary -> it
                        is Map<*, *> -> {
                            CpkSummary(
                                it["name"].toString(),
                                it["version"].toString(),
                                it["signerSummaryHash"]?.toString(),
                                it["fileChecksum"].toString()
                            )
                        }
                        else -> throw CordaRuntimeException(
                            "Transaction metadata representation error: expected CPK metadata but found [$data]")
                    }
                }
            }
            else -> throw CordaRuntimeException(
                "Transaction metadata representation error: expected list of CPK metadata but found [$data]")
        }
    }

    fun getDigestSettings(): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this[DIGEST_SETTINGS_KEY] as Map<String, Any>
    }
}