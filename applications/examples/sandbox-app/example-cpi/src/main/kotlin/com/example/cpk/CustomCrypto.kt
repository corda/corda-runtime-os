package com.example.cpk

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ CustomCrypto::class ])
class CustomCrypto @Activate constructor(
    @Reference
    private val digestService: DigestService
) : CordaFlowInjectable {
    fun hashOf(bytes: ByteArray): SecureHash {
        return digestService.hash(bytes, DigestAlgorithmName("SHA-256-TRIPLE"))
    }
}
