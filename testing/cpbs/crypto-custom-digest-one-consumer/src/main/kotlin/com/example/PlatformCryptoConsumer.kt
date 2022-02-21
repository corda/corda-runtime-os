package com.example

import net.corda.v5.application.flows.Flow
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class PlatformCryptoConsumer @Activate constructor(
    @Reference
    private val digestService: DigestService
) : Flow<SecureHash> {
    override fun call(): SecureHash {
        return digestService.hash("some random string".toByteArray(), DigestAlgorithmName("SHA-256"))
    }
}
