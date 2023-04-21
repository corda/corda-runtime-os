package net.corda.libs.configuration.secret

interface SecretEncryptor {
    fun encrypt(plainText: String, salt: String, passphrase: String): String
}