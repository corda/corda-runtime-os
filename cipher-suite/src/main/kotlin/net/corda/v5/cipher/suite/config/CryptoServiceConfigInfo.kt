package net.corda.v5.cipher.suite.config

class CryptoServiceConfigInfo(
    val category: String,
    val effectiveSandboxId: String,
    val config: CryptoServiceConfig
)