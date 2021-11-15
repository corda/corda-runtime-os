package org.example

import net.corda.v5.application.flows.Flow
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.example.crypto.QuadSha256Digest
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * NOTE:  we're using OSGi service injection here for proof of concept of custom digests only.
 *
 * DO NOT expect this to work in a real cordapp (or a future version of this).
 */
@Component(name = "crypto.consumer.1")
class CryptoConsumer
@Activate constructor(@Reference private val cipherSuiteFactory: CipherSuiteFactory) : Flow<SecureHash> {
    override fun call(): SecureHash {
        val digestService = cipherSuiteFactory.getDigestService()
        return digestService.hash("some random string for PoC".toByteArray(), DigestAlgorithmName(QuadSha256Digest.ALGORITHM))
    }
}
