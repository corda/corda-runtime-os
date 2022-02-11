package com.example

import com.example.crypto.TripleSha256Digest
import net.corda.v5.application.flows.Flow
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
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
@Activate constructor(@Reference private val digestService: DigestService) : Flow<SecureHash> {
    override fun call(): SecureHash {
        return digestService.hash("some random string for PoC".toByteArray(), DigestAlgorithmName(TripleSha256Digest.ALGORITHM))
    }
}
