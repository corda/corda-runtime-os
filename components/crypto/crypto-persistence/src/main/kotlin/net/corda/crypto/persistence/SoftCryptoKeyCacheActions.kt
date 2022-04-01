package net.corda.crypto.persistence

interface SoftCryptoKeyCacheActions : AutoCloseable {
    fun saveWrappingKey(alias: String, key: WrappingKey)
    fun findWrappingKey(alias: String): WrappingKey?
}