package net.corda.v5.cipher.suite.config

import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import java.time.Duration

class CryptoServiceConfig(
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val timeout: Duration = Duration.ofSeconds(5),
    val serviceConfig: Map<String, Any?> = mapOf(),
    val defaultSignatureScheme: String = ECDSA_SECP256R1_CODE_NAME
) {
    companion object {
        const val DEFAULT_SERVICE_NAME = "default"
    }
}