package org.example

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class CryptoConsumerTryAndUseOtherDigest
@Activate constructor(@Reference private val digestService: DigestService) : SubFlow<SecureHash> {
    override fun call(): SecureHash {
        // THIS IS EXPECTED TO FAIL
        return digestService.hash("tttt".toByteArray(), DigestAlgorithmName("SHA-256-TRIPLE"))
    }
}
