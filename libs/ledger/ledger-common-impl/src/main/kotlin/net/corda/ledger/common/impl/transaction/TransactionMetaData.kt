package net.corda.ledger.common.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class TransactionMetaData(
    private val properties: LinkedHashMap<String, Any>
    ) {

    operator fun get(key: String): Any? = properties[key]

    val entries: Set<Map.Entry<String, Any>>
        get() = properties.entries

    companion object {
        const val LEDGER_MODEL_KEY = "ledgerModel"
        const val LEDGER_VERSION_KEY = "ledgerVersion"
        const val CPK_IDENTIFIERS_KEY = "cpkIdentifiers"
        const val DIGEST_SETTINGS_KEY = "digestSettings"
        const val PLATFORM_VERSION_KEY = "platformVersion"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TransactionMetaData) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    fun getLedgerModel(): String{
        return this[LEDGER_MODEL_KEY].toString()
    }
    fun getLedgerVersion(){
        this[LEDGER_VERSION_KEY]
    }
    fun cpkIdentifiers(){
        this[CPK_IDENTIFIERS_KEY]
    }
    fun getDigestSettings(): LinkedHashMap<String, Any>{
        @Suppress("UNCHECKED_CAST")
        return this[DIGEST_SETTINGS_KEY] as LinkedHashMap<String, Any>
    }
}