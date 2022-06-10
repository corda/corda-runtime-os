package net.corda.crypto.tck.impl

import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.tck.ExecutionOptions
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.crypto.sha256Bytes
import java.util.UUID

class ComplianceSpec(
    val options: ExecutionOptions
) {
    fun createService(providers: CryptoServiceProviderMap): CryptoService =
        CryptoServiceDecorator.create(
            provider = providers.get(options.serviceName),
            serviceConfig = CryptoServiceDecorator.objectMapper.writeValueAsBytes(options.serviceConfig),
            maxAttempts = options.maxAttempts,
            attemptTimeout = options.attemptTimeout
        )

    fun generateRandomIdentifier(len: Int = 12) =
        UUID.randomUUID().toString().toByteArray().sha256Bytes().toHexString().take(len)
}