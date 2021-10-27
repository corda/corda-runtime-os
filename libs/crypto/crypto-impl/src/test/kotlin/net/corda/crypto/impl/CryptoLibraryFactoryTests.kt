package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Duration
import kotlin.test.assertSame
import kotlin.test.fail

class CryptoLibraryFactoryTests {
    @Test
    fun `Should delegate service creation to cipher suite factory`() {
        val digest = mock< DigestService>()
        val verifier = mock<SignatureVerificationService>()
        val metadata = mock<CipherSchemeMetadata>()
        val cipherSuiteFactory = mock<CipherSuiteFactory>()
        whenever(
            cipherSuiteFactory.getDigestService()
        ).thenReturn(digest)
        whenever(
            cipherSuiteFactory.getSignatureVerificationService()
        ).thenReturn(verifier)
        whenever(
            cipherSuiteFactory.getSchemeMap()
        ).thenReturn(metadata)
        val factory = CryptoLibraryFactoryImpl(cipherSuiteFactory)
        assertSame(digest, factory.getDigestService())
        assertSame(verifier, factory.getSignatureVerificationService())
        assertSame(metadata, factory.getCipherSchemeMetadata())
    }

    @Disabled("Use only to evaluate the factory performance.")
    @Test
    fun `Should perform well`() {
        val cipherSuiteFactory = CipherSuiteFactoryImpl(
            listOf(
                CipherSchemeMetadataProviderImpl()
            ),
            listOf(
                SignatureVerificationServiceProviderImpl()
            ),
            listOf(
                DigestServiceProviderImpl()
            )
        )
        val factory = CryptoLibraryFactoryImpl(cipherSuiteFactory)
        val publicKey: PublicKey = KeyPairGenerator.getInstance("RSA").genKeyPair().public
        val encoded = factory.getKeyEncodingService().encodeAsByteArray(publicKey)
        val encodingMessage = elapsed{
            for (i in 1..1_000_000) {
                val encoder = factory.getKeyEncodingService()
                encoder.encodeAsByteArray(publicKey)
            }
        }.second
        val decodingMessage = elapsed{
            for (i in 1..1_000_000) {
                val encoder = factory.getKeyEncodingService()
                encoder.decodePublicKey(encoded)
            }
        }.second
        fail("Encoding: $encodingMessage, ${System.lineSeparator()}Decoding: $decodingMessage")
        // Approximately (so we can always get the service from factory):
        // getKeyEncodingService = 6M/second
        // getKeyEncodingService + encode = 3M/second (occasionally as high as 10M/second)
        // getKeyEncodingService + decode = 1M/minute
    }

    private fun elapsed(block: () -> Unit): Pair<Duration, String> {
        val start = System.nanoTime()
        block()
        val duration = Duration.ofNanos(System.nanoTime() - start)
        return Pair(
            duration,
            "Executed in $duration (${duration.toMillis()} ms.)."
        )
    }
}