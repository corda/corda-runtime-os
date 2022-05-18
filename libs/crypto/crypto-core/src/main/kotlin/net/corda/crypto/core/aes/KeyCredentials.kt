package net.corda.crypto.core.aes

/**
 * Parameters which are required to derive a key.
 */
class KeyCredentials(
    val passphrase: String,
    val salt: String
) {
    init {
        require(passphrase.isNotBlank()) {
            "The passphrase cannot be blank."
        }
        require(salt.isNotBlank()) {
            "The salt cannot be blank."
        }
    }
}