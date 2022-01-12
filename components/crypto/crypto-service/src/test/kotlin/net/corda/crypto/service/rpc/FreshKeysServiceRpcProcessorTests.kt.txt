package net.corda.crypto.service.rpc

import net.corda.crypto.impl.persistence.SigningKeyRecord
import net.corda.crypto.service.CryptoFactory
import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
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
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreshKeysServiceRpcProcessorTests {
    private class FreshKeySigningServiceWrapper(
        private val impl: FreshKeySigningService
    ) : FreshKeySigningService {
        companion object {
            val recordedContexts = ConcurrentHashMap<String, Map<String, String>>()
        }

        override fun freshKey(context: Map<String, String>): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.freshKey(context)
        }

        override fun freshKey(externalId: UUID, context: Map<String, String>): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.freshKey(externalId, context)
        }

        override fun sign(
            publicKey: PublicKey,
            data: ByteArray,
            context: Map<String, String>
        ): DigitalSignature.WithKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(publicKey, data, context)
        }

        override fun sign(
            publicKey: PublicKey,
            signatureSpec: SignatureSpec,
            data: ByteArray,
            context: Map<String, String>
        ): DigitalSignature.WithKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(publicKey, signatureSpec, data, context)
        }

        override fun ensureWrappingKey() {
            return impl.ensureWrappingKey()
        }

        override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
            return impl.filterMyKeys(candidateKeys)
        }
    }

    companion object {
        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var verifier: SignatureVerificationService
        private lateinit var processor: FreshKeysServiceRpcProcessor
        private lateinit var cryptoFactory: CryptoFactory
        private lateinit var freshKeySigningServiceWrapper: FreshKeySigningServiceWrapper

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            verifier = cryptoMocks.factories.cryptoLibrary.getSignatureVerificationService()
            cryptoFactory = mock()
            freshKeySigningServiceWrapper = FreshKeySigningServiceWrapper(
                cryptoMocks.factories.cryptoClients(memberId).getFreshKeySigningService()
            )
            whenever(
                cryptoFactory.getFreshKeySigningService(memberId)
            ).thenReturn(freshKeySigningServiceWrapper)
            whenever(
                cryptoFactory.cipherSchemeMetadata
            ).thenReturn(schemeMetadata)
            processor = FreshKeysServiceRpcProcessor(
                cryptoFactory
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
            KeyValuePairList(
                listOf(
                    KeyValuePair("key1", "value1"),
                    KeyValuePair("key2", "value2")
                )
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
                actual.other.items.size == expected.other.items.size &&
                        actual.other.items.containsAll(expected.other.items) &&
                        expected.other.items.containsAll(actual.other.items)
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

        private fun get(publicKey: PublicKey): SigningKeyRecord? {
            return cryptoMocks.factories.cryptoServices.signingPersistence.getValue(memberId).persistence.get(
                "$memberId:${publicKey.sha256Bytes().toHexString()}"
            )
        }
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key with associating it with any id and be able to sign using default and custom schemes`() {
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val context = getWireRequestContext()
        val operationContext = listOf(
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context,
                WireFreshKeysFreshKey(null, KeyValuePairList(operationContext))
            ),
            future
        )
        val result = future.get()
        val operationContextMap = FreshKeySigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(2, operationContext.size)
        assertEquivalent(context, result.context)
        assertThat(result.response, instanceOf(WirePublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result.response as WirePublicKey).key.array())
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
        val context = getWireRequestContext()
        val operationContext = listOf(
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context,
                WireFreshKeysFreshKey(externalId.toString(), KeyValuePairList(operationContext))
            ),
            future
        )
        val result = future.get()
        val operationContextMap = FreshKeySigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquivalent(context, result.context)
        assertThat(result.response, instanceOf(WirePublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result.response as WirePublicKey).key.array())
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
        val context = getWireRequestContext()
        val operationContext = listOf(
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context,
                WireFreshKeysFreshKey(null, KeyValuePairList(operationContext))
            ),
            future
        )
        val result = future.get()
        val operationContextMap = FreshKeySigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquivalent(context, result.context)
        assertThat(result.response, instanceOf(WirePublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result.response as WirePublicKey).key.array())
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
                        "BAD-DIGEST-ALGORITHM"
                    ),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
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
        val operationContext = listOf(
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future2 = CompletableFuture<WireFreshKeysResponse>()
        processor.onNext(
            WireFreshKeysRequest(
                context2,
                WireFreshKeysSign(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(operationContext)
                )
            ),
            future2
        )
        val result2 = future2.get()
        val operationContextMap = FreshKeySigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
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
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
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
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
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