package net.corda.v5.cipher.suite.config

import java.time.Duration

class CryptoServiceConfig(
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val timeout: Duration = Duration.ofSeconds(5),
    val serviceConfig: Map<String, Any?> = mapOf()
) {
    companion object {
        const val DEFAULT_SERVICE_NAME = "default"
    }
}