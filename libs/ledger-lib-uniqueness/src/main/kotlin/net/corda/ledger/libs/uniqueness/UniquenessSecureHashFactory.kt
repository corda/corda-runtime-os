package net.corda.ledger.libs.uniqueness

import net.corda.v5.crypto.SecureHash

interface UniquenessSecureHashFactory {
    fun createSecureHash(algorithm: String, bytes: ByteArray): SecureHash
    fun getBytes(hash: SecureHash): ByteArray
    fun parseSecureHash(hashString: String): SecureHash
}
