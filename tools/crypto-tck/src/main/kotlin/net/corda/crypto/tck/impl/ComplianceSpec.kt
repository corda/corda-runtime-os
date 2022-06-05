package net.corda.crypto.tck.impl

import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.tck.ExecutionOptions
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.sha256Bytes
import java.util.UUID

class ComplianceSpec(
    val options: ExecutionOptions
) {
    fun createService(providers: CryptoServiceProviderMap): CryptoService =
        CryptoServiceDecorator.create(
            provider = providers.get(options.serviceName),
            serviceConfig = CryptoServiceDecorator.objectMapper.writeValueAsBytes(options.serviceConfig),
            retries = options.retries,
            timeout = options.timeout
        )

    fun getFlattenedSchemesAndSignatureSpecs(): List<Pair<String, SignatureSpec>> {
        val list = mutableListOf<Pair<String, SignatureSpec>>()
        options.signatureSpecs.forEach {
            it.value.forEach { s -> list.add(Pair(it.key, s)) }
        }
        return list
    }

    fun generateRandomIdentifier(len: Int = 12) =
        UUID.randomUUID().toString().toByteArray().sha256Bytes().toHexString().take(len)
}