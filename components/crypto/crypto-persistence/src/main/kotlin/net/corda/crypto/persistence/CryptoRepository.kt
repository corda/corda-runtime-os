package net.corda.crypto.persistence

import java.io.Closeable

/**
 * Crypto JPA repository
 *
 * See https://thorben-janssen.com/implementing-the-repository-pattern-with-jpa-and-hibernate/
 */
interface CryptoRepository : Closeable {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo)
    fun findWrappingKey(alias: String): WrappingKeyInfo?
}