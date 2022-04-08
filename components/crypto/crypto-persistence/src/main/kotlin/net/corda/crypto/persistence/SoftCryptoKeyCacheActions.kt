package net.corda.crypto.persistence

interface SoftCryptoKeyCacheActions : AutoCloseable {
    fun saveWrappingKey(alias: String, key: WrappingKey, failIfExists: Boolean)
    fun findWrappingKey(alias: String): WrappingKey?
}