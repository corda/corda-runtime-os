package net.corda.cipher.suite.impl

class DefaultCryptoServiceConfig(
    val partition: String?,
    val passphrase: String?,
    val salt: String?
)