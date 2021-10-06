package net.corda.v5.cipher.suite

class CryptoServiceContext<T>(
    val category: String,
    val memberId: String,
    val cipherSuiteFactory: CipherSuiteFactory,
    val config: T
)