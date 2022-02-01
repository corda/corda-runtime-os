package net.corda.v5.cipher.suite

class CryptoServiceContext<T>(
    val category: String,
    val tenantId: String,
    val config: T
)