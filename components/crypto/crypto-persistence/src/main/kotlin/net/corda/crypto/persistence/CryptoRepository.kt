package net.corda.crypto.persistence

import java.io.Closeable

/**
 * Crypto JPA repository for a specific tenant ID. So, in general there will be different
 * instances of this for dealing with for instance P2P at cluster level, versus different
 * instances for difference virtual nodes.
 *
 * In the future, we plan to move all the remaining persistence code here.
 *
 * See https://thorben-janssen.com/implementing-the-repository-pattern-with-jpa-and-hibernate/
 */
interface CryptoRepository : Closeable {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo)
    fun findWrappingKey(alias: String): WrappingKeyInfo?
}