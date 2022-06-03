package net.corda.crypto.persistence.hsm

interface HSMStore : AutoCloseable {
    fun act(): HSMStoreActions
    fun <R> act(block: (HSMStoreActions) -> R): R {
        return act().use(block)
    }
}