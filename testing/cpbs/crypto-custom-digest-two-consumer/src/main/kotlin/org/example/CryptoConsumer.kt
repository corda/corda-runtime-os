package org.example

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.SubFlow
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
@Component
class CryptoConsumer
@Activate constructor(@Reference private val digestService: DigestService) : SubFlow<SecureHash> {
    override fun call(): SecureHash {
        return digestService.hash("some random string for PoC".toByteArray(), DigestAlgorithmName(QuadSha256Digest.ALGORITHM))
    }
}
