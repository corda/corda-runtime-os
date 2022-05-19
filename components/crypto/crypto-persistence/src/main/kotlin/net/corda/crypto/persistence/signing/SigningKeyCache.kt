package net.corda.crypto.persistence.signing

interface SigningKeyCache : AutoCloseable {
    fun act(tenantId: String): SigningKeyCacheActions
    fun <R> act(tenantId: String, block: (SigningKeyCacheActions) -> R): R {
        return act(tenantId).use(block)
    }
}

