package net.corda.crypto.persistence.soft

import net.corda.crypto.core.aes.WrappingKey

interface SoftCryptoKeyCacheActions : AutoCloseable {
    fun saveWrappingKey(alias: String, key: WrappingKey, failIfExists: Boolean)
    fun findWrappingKey(alias: String): WrappingKey?
}