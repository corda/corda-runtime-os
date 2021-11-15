package org.example

import net.corda.v5.application.flows.Flow
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(name = "crypto.consumer.2")
class CryptoConsumerTryAndUseOtherDigest
@Activate constructor(@Reference private val cipherSuiteFactory: CipherSuiteFactory) : Flow<SecureHash> {
    override fun call(): SecureHash {
        val digestService = cipherSuiteFactory.getDigestService()
        // THIS IS EXPECTED TO FAIL
        return digestService.hash("tttt".toByteArray(), DigestAlgorithmName("SHA-256-TRIPLE"))
    }
}
