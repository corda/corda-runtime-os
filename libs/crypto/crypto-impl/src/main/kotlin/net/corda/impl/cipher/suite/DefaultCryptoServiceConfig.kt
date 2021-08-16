package net.corda.impl.cipher.suite

class DefaultCryptoServiceConfig(
    val partition: String?,
    val passphrase: String?,
    val salt: String?
)