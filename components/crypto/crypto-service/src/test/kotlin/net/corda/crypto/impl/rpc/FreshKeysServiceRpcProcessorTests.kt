package net.corda.crypto.impl.rpc

import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.WireKeyValuePair
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WireRequestContext
import net.corda.data.crypto.wire.WireResponseContext
import net.corda.data.crypto.wire.WireSignatureSpec
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysEnsureWrappingKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFilterMyKeys
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFreshKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSign
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSignWithSpec
import net.corda.data.crypto.wire.signing.WireSigningGetSupportedSchemes
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.sha256Bytes
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreshKeysServiceRpcProcessorTests {
    companion object {
        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var verifier: SignatureVerificationService
        private lateinit var processor: FreshKeysServiceRpcProcessor

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            verifier = cryptoMocks.factories.cryptoClients.getSignatureVerificationService()
            processor = FreshKeysServiceRpcProcessor(
                cryptoMocks.factories.cryptoServices
            )
            val context = getWireRequestContext()
            val future = CompletableFuture<WireFreshKeysResponse>()
            processor.onNext(
                WireFreshKeysRequest(
                    context,
                    WireFreshKeysEnsureWrappingKey()
                ),
                future
            )
            val result = future.get()
            assertEquivalent(context, result.context)
            assertThat(result.response, instanceOf(WireNoContentValue::class.java))
        }

        private fun getWireRequestContext(): WireRequestContext = WireRequestContext(
            "test-component",
            Instant.now(),
            memberId,
            listOf(
                WireKeyValuePair("key1", "value1"),
                WireKeyValuePair("key2", "value2")
            )
        )

        private fun assertEquivalent(expected: WireRequestContext, actual: WireResponseContext) {
            val now = Instant.now()
            assertEquals(expected.memberId, actual.memberId)
            assertEquals(expected.requestingComponent, actual.requestingComponent)
            assertEquals(expected.requestTimestamp, actual.requestTimestamp)
            assertThat(
                actual.responseTimestamp.epochSecond,
                allOf(greaterThanOrEqualTo(expected.requestTimestamp.epochSecond), lessThanOrEqualTo(now.epochSecond))
            )
            assertTrue(
                actual.other.size == expected.other.size &&
                        actual.other.containsAll(expected.other) &&
                        expected.other.containsAll(actual.other)
            )
        }

        private fun getFullCustomSignatureSpec(): SignatureSpec {
            val scheme = cryptoMocks.factories.defaultFreshKeySignatureScheme
            return when (scheme.algorithmName) {
                "RSA" -> SignatureSpec(
                        signatureName = "RSA/NONE/PKCS1Padding",
                        customDigestName = DigestAlgorithmName.SHA2_512
                    )
                "EC" -> SignatureSpec(
                        signatureName = "NONEwithECDSA",
                        customDigestName = DigestAlgorithmName.SHA2_512
                    )
                else -> SignatureSpec(
                        signatureName = "NONEwith${scheme.algorithmName}",
                        customDigestName = DigestAlgorithmName.SHA2_512
                    )
            }
        }

        private fun getCustomSignatureSpec(): SignatureSpec? {
            val scheme = cryptoMocks.factories.defaultFreshKeySignatureScheme
            return when (scheme.algorithmName) {
                "RSA" -> null
                "EC" -> SignatureSpec(
                    signatureName = "SHA512withECDSA"
                )
                else -> SignatureSpec(
                    signatureName = "SHA512with${scheme.algorithmName}",
                    customDigestName = DigestAlgorithmName.SHA2_512
                )
            }
        }

        private fun get(publicKey: PublicKey): SigningPersistentKeyInfo? {
            return cryptoMocks.signingPersistentKeyCache.data.values.firstOrNull {
                it.first.publicKeyHash == "$memberId:${publicKey.sha256Bytes().toHexString()}"
            }?.first
        }
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key with associating it with any id and be able to sign using default and custom schemes`() {
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val context1 = getWireRequestContext()
        val future1 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context1,
                WireFreshKeysFreshKey()
            ),
            future1
        )
        val result1 = future1.get()
        assertEquivalent(context1, result1.context)
        assertThat(result1.response, instanceOf(WirePublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result1.response as WirePublicKey).key.array())
        val info = get(publicKey)
        assertNotNull(info)
        assertNull(info.externalId)
        testSigning(publicKey, data)
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key associated with external id and be able to sign using default and custom schemes`() {
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val externalId = UUID.randomUUID()
        val context1 = getWireRequestContext()
        val future1 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context1,
                WireFreshKeysFreshKey(externalId.toString())
            ),
            future1
        )
        val result1 = future1.get()
        assertEquivalent(context1, result1.context)
        assertThat(result1.response, instanceOf(WirePublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result1.response as WirePublicKey).key.array())
        val info = get(publicKey)
        assertNotNull(info)
        assertEquals(externalId, info.externalId)
        testSigning(publicKey, data)
    }

    @Test
    @Timeout(5)
    fun `Should complete future exceptionally in case of service failure`() {
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val context1 = getWireRequestContext()
        val future1 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context1,
                WireFreshKeysFreshKey()
            ),
            future1
        )
        val result1 = future1.get()
        assertEquivalent(context1, result1.context)
        assertThat(result1.response, instanceOf(WirePublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result1.response as WirePublicKey).key.array())
        val info = get(publicKey)
        assertNotNull(info)
        assertNull(info.externalId)
        // sign using invalid custom scheme
        val context3 = getWireRequestContext()
        val future3 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context3,
                WireFreshKeysSignWithSpec(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    WireSignatureSpec(
                        "BAD-SIGNATURE-ALGORITHM",
                        "BAD-DIGEST-ALGORITHM"),
                    ByteBuffer.wrap(data)
                )
            ),
            future3
        )
        val exception = assertThrows<ExecutionException> {
            future3.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should complete future exceptionally in case of unknown request`() {
        val context = getWireRequestContext()
        val future = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context,
                WireSigningGetSupportedSchemes()
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
        assertThat(exception.cause?.cause, instanceOf(CryptoServiceBadRequestException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail filtering my keys as it's not implemented yet`() {
        val context1 = getWireRequestContext()
        val future1 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context1,
                WireFreshKeysFilterMyKeys(emptyList())
            ),
            future1
        )
        val exception = assertThrows<ExecutionException> {
            future1.get()
        }
        assertNotNull(exception.cause)
        assertNotNull(exception.cause?.cause)
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
        assertThat(exception.cause?.cause, instanceOf(NotImplementedError::class.java))
    }

    private fun testSigning(publicKey: PublicKey, data: ByteArray) {
        // sign using default scheme
        val context2 = getWireRequestContext()
        val future2 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context2,
                WireFreshKeysSign(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    ByteBuffer.wrap(data)
                )
            ),
            future2
        )
        val result2 = future2.get()
        assertEquivalent(context2, result2.context)
        assertThat(result2.response, instanceOf(WireSignatureWithKey::class.java))
        val signature2 = result2.response as WireSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature2.publicKey.array()))
        verifier.verify(publicKey, signature2.bytes.array(), data)
        // sign using full custom scheme
        val signatureSpec3 = getFullCustomSignatureSpec()
        val context3 = getWireRequestContext()
        val future3 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context3,
                WireFreshKeysSignWithSpec(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    WireSignatureSpec(signatureSpec3.signatureName, signatureSpec3.customDigestName?.name),
                    ByteBuffer.wrap(data)
                )
            ),
            future3
        )
        val result3 = future3.get()
        assertEquivalent(context3, result3.context)
        assertThat(result3.response, instanceOf(WireSignatureWithKey::class.java))
        val signature3 = result3.response as WireSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature3.publicKey.array()))
        verifier.verify(publicKey, signatureSpec3, signature3.bytes.array(), data)
        // sign using custom scheme
        val signatureSpec4 = getCustomSignatureSpec()
        assumeTrue(signatureSpec4 != null)
        val context4 = getWireRequestContext()
        val future4 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context4,
                WireFreshKeysSignWithSpec(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    WireSignatureSpec(signatureSpec4!!.signatureName, signatureSpec4.customDigestName?.name),
                    ByteBuffer.wrap(data)
                )
            ),
            future4
        )
        val result4 = future4.get()
        assertEquivalent(context4, result4.context)
        assertThat(result4.response, instanceOf(WireSignatureWithKey::class.java))
        val signature4 = result4.response as WireSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature4.publicKey.array()))
        verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }
}