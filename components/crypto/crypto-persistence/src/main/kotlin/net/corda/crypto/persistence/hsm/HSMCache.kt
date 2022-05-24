package net.corda.crypto.persistence.hsm

interface HSMCache : AutoCloseable {
    fun act(): HSMCacheActions
    fun <R> act(block: (HSMCacheActions) -> R): R {
        return act().use(block)
    }
}