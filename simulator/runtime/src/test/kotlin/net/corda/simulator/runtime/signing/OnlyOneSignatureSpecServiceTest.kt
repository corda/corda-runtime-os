package net.corda.simulator.runtime.signing

import net.corda.simulator.runtime.testutils.generateKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class OnlyOneSignatureSpecServiceTest {

    @Test
    fun `should always return a never-actually-used ECDSA SHA256 spec`() {
        val service = OnlyOneSignatureSpecService()
        val key = generateKey()
        val expected = SignatureSpec.ECDSA_SHA256

        assertThat(service.compatibleSignatureSpecs(key), `is`(listOf(expected)))
        assertThat(service.compatibleSignatureSpecs(key, DigestAlgorithmName.SHA2_384), `is`(listOf(expected)))
        assertThat(service.defaultSignatureSpec(key), `is`(expected))
        assertThat(service.defaultSignatureSpec(key, DigestAlgorithmName.SHA2_384), `is`(expected))
    }
}