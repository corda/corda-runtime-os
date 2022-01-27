package net.corda.libs.configuration.secret

interface SecretDecryptor {
    fun decrypt(cypherText: String, salt: String, passphrase: String): String
}