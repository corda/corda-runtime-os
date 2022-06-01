package net.corda.crypto.persistence.signing

interface SigningKeyStore : AutoCloseable {
    fun act(tenantId: String): SigningKeyStoreActions
    fun <R> act(tenantId: String, block: (SigningKeyStoreActions) -> R): R {
        return act(tenantId).use(block)
    }
}

