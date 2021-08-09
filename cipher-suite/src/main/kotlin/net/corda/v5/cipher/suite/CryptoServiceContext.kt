package net.corda.v5.cipher.suite

class CryptoServiceContext<T>(
    val category: String,
    val sandboxId: String,
    val cipherSuiteFactory: CipherSuiteFactory,
    val config: T
)