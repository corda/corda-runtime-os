package net.corda.crypto.persistence

interface CryptoRepository {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo)
    fun findWrappingKey(alias: String): WrappingKeyInfo?
}