package net.corda.crypto.client.rpc

import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoConsts
import net.corda.crypto.client.generateKeyPair
import net.corda.crypto.client.sign
import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WirePublicKeys
import net.corda.data.crypto.wire.WireResponseContext
import net.corda.data.crypto.wire.WireSignature
import net.corda.data.crypto.wire.WireSignatureSchemes
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.crypto.wire.signing.WireSigningFindPublicKey
import net.corda.data.crypto.wire.signing.WireSigningGenerateKeyPair
import net.corda.data.crypto.wire.signing.WireSigningGetSupportedSchemes
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.data.crypto.wire.signing.WireSigningSign
import net.corda.data.crypto.wire.signing.WireSigningSignWithAlias
import net.corda.data.crypto.wire.signing.WireSigningSignWithAliasSpec
import net.corda.data.crypto.wire.signing.WireSigningSignWithSpec
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SigningServiceClientTests {
    companion object {
        private const val requestingComponent = "requesting-component"
        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
        }
    }

    private lateinit var sender: RPCSender<WireSigningRequest, WireSigningResponse>

    @BeforeEach
    fun setupEach() {
        sender = mock()
    }

    @Test
    @Timeout(5)
    fun `Should execute supportedSchemes request`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        setupCompletedResponse {
            WireSignatureSchemes(
                schemeMetadata.schemes.map { it.codeName }
            )
        }
        val nowBefore = Instant.now()
        val result = client.supportedSchemes
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningGetSupportedSchemes::class.java))
        assertEquals(schemeMetadata.schemes.size, result.size)
        schemeMetadata.schemes.forEach {
            assertTrue(result.contains(it))
        }
    }

    @Test
    @Timeout(5)
    fun `Should find public key`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        setupCompletedResponse {
            WirePublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val alias = UUID.randomUUID().toString()
        val nowBefore = Instant.now()
        val result = client.findPublicKey(alias)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningFindPublicKey::class.java))
        assertEquals(alias, (requests.firstValue.request as WireSigningFindPublicKey).alias)
        assertNotNull(result)
        assertEquals(keyPair.public, result)
    }

    @Test
    @Timeout(5)
    fun `Should return null when public key is not found`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        setupCompletedResponse {
            WireNoContentValue()
        }
        val alias = UUID.randomUUID().toString()
        val nowBefore = Instant.now()
        val result = client.findPublicKey(alias)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningFindPublicKey::class.java))
        assertEquals(alias, (requests.firstValue.request as WireSigningFindPublicKey).alias)
        assertNull(result)
    }

    @Test
    @Timeout(5)
    fun `Should execute generateKeyPair request`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        setupCompletedResponse {
            WirePublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val alias = UUID.randomUUID().toString()
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.generateKeyPair(alias, context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningGenerateKeyPair::class.java))
        assertEquals(alias, (requests.firstValue.request as WireSigningGenerateKeyPair).alias)
        assertEquals(2, (requests.firstValue.request as WireSigningGenerateKeyPair).context.items.size)
        assertTrue((requests.firstValue.request as WireSigningGenerateKeyPair).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireSigningGenerateKeyPair).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign request`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = sign(schemeMetadata, keyPair.private, data)
        setupCompletedResponse {
            WireSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.sign(keyPair.public, data, context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningSign::class.java))
        assertArrayEquals(
            schemeMetadata.encodeAsByteArray(keyPair.public),
            (requests.firstValue.request as WireSigningSign).publicKey.array()
        )
        assertArrayEquals(
            data,
            (requests.firstValue.request as WireSigningSign).bytes .array()
        )
        assertEquals(2, (requests.firstValue.request as WireSigningSign).context.items.size)
        assertTrue((requests.firstValue.request as WireSigningSign).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireSigningSign).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result.by)
        assertArrayEquals(signature, result.bytes)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign with custom signature spec request`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        val spec = SignatureSpec("NONEwithECDSA", DigestAlgorithmName.SHA2_256)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = sign(schemeMetadata, keyPair.private, data)
        setupCompletedResponse {
            WireSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.sign(keyPair.public, spec, data, context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningSignWithSpec::class.java))
        assertArrayEquals(
            schemeMetadata.encodeAsByteArray(keyPair.public),
            (requests.firstValue.request as WireSigningSignWithSpec).publicKey.array()
        )
        assertArrayEquals(
            data,
            (requests.firstValue.request as WireSigningSignWithSpec).bytes .array()
        )
        assertEquals(
            "NONEwithECDSA",
            (requests.firstValue.request as WireSigningSignWithSpec).signatureSpec.signatureName
        )
        assertEquals(
            "SHA-256",
            (requests.firstValue.request as WireSigningSignWithSpec).signatureSpec.customDigestName
        )
        assertEquals(2, (requests.firstValue.request as WireSigningSignWithSpec).context.items.size)
        assertTrue((requests.firstValue.request as WireSigningSignWithSpec).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireSigningSignWithSpec).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result.by)
        assertArrayEquals(signature, result.bytes)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign request using alias`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = sign(schemeMetadata, keyPair.private, data)
        setupCompletedResponse {
            WireSignature(
                ByteBuffer.wrap(signature)
            )
        }
        val alias = UUID.randomUUID().toString()
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.sign(alias, data, context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningSignWithAlias::class.java))
        assertEquals(alias, (requests.firstValue.request as WireSigningSignWithAlias).alias)
        assertArrayEquals(
            data,
            (requests.firstValue.request as WireSigningSignWithAlias).bytes .array()
        )
        assertEquals(2, (requests.firstValue.request as WireSigningSignWithAlias).context.items.size)
        assertTrue((requests.firstValue.request as WireSigningSignWithAlias).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireSigningSignWithAlias).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertArrayEquals(signature, result)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign with custom signature spec request using alias`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        val spec = SignatureSpec("NONEwithECDSA", DigestAlgorithmName.SHA2_256)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = sign(schemeMetadata, keyPair.private, data)
        setupCompletedResponse {
            WireSignature(
                ByteBuffer.wrap(signature)
            )
        }
        val alias = UUID.randomUUID().toString()
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.sign(alias, spec, data, context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningSignWithAliasSpec::class.java))
        assertEquals(alias, (requests.firstValue.request as WireSigningSignWithAliasSpec).alias)
        assertArrayEquals(
            data,
            (requests.firstValue.request as WireSigningSignWithAliasSpec).bytes .array()
        )
        assertEquals(
            "NONEwithECDSA",
            (requests.firstValue.request as WireSigningSignWithAliasSpec).signatureSpec.signatureName
        )
        assertEquals(
            "SHA-256",
            (requests.firstValue.request as WireSigningSignWithAliasSpec).signatureSpec.customDigestName
        )
        assertEquals(2, (requests.firstValue.request as WireSigningSignWithAliasSpec).context.items.size)
        assertTrue((requests.firstValue.request as WireSigningSignWithAliasSpec).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireSigningSignWithAliasSpec).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertArrayEquals(signature, result)
    }

    @Test
    @Timeout(5)
    fun `Should fail when response context member id does not match the request`() {
        val client = createClient()
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, WireSigningRequest::class.java)
            val future = CompletableFuture<WireSigningResponse>()
            future.complete(
                WireSigningResponse(
                    WireResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        Instant.now(),
                        UUID.randomUUID().toString(),
                        req.context.other
                    ), WireNoContentValue()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceException> {
            client.findPublicKey(UUID.randomUUID().toString())
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail when response context requestingComponent does not match the request`() {
        val client = createClient()
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, WireSigningRequest::class.java)
            val future = CompletableFuture<WireSigningResponse>()
            future.complete(
                WireSigningResponse(
                    WireResponseContext(
                        UUID.randomUUID().toString(),
                        req.context.requestTimestamp,
                        Instant.now(),
                        req.context.memberId,
                        req.context.other
                    ), WireNoContentValue()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceException> {
            client.findPublicKey(UUID.randomUUID().toString())
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail when response class is not expected`() {
        val client = createClient()
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, WireSigningRequest::class.java)
            val future = CompletableFuture<WireSigningResponse>()
            future.complete(
                WireSigningResponse(
                    WireResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        Instant.now(),
                        req.context.memberId,
                        req.context.other
                    ), WirePublicKeys(emptyList())
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceException> {
            client.findPublicKey(UUID.randomUUID().toString())
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail when sendRequest throws CryptoServiceLibraryException exception`() {
        val client = createClient()
        val error = CryptoServiceLibraryException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<Exception> {
            client.findPublicKey(UUID.randomUUID().toString())
        }
        assertSame(error, exception)
    }

    @Test
    @Timeout(5)
    fun `Should fail when sendRequest throws TimeoutException exception exceeding number retries`() {
        val client = createClient()
        val timeout1 = TimeoutException()
        val timeout2 = TimeoutException()
        whenever(sender.sendRequest(any())).then { throw timeout1 }.then { throw timeout2 }
        val exception = assertThrows<CryptoServiceTimeoutException> {
            client.findPublicKey(UUID.randomUUID().toString())
        }
        assertNotNull(exception.cause)
        assertSame(timeout2, exception.cause)
    }

    @Test
    @Timeout(5)
    fun `Should eventually succeed request after retry`() {
        val requests = argumentCaptor<WireSigningRequest>()
        val client = createClient()
        whenever(
            sender.sendRequest(any())
        ).then { throw TimeoutException() }.then {
            val req = it.getArgument(0, WireSigningRequest::class.java)
            val future = CompletableFuture<WireSigningResponse>()
            future.complete(
                WireSigningResponse(
                    WireResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        Instant.now(),
                        req.context.memberId,
                        req.context.other
                    ), WireNoContentValue()
                )
            )
            future
        }
        val alias = UUID.randomUUID().toString()
        val nowBefore = Instant.now()
        val result = client.findPublicKey(alias)
        val nowAfter = Instant.now()
        Mockito.verify(sender, times(2)).sendRequest(requests.capture())
        validatePassedRequestContext(requests, nowBefore, nowAfter)
        assertThat(requests.firstValue.request, instanceOf(WireSigningFindPublicKey::class.java))
        assertEquals(alias, (requests.firstValue.request as WireSigningFindPublicKey).alias)
        assertNull(result)
    }

    private fun createClient(): SigningServiceClient =
        SigningServiceClient(
            memberId = memberId,
            category = CryptoConsts.CryptoCategories.LEDGER,
            requestingComponent = requestingComponent,
            clientTimeout = Duration.ofSeconds(15),
            clientRetries = 1,
            schemeMetadata = schemeMetadata,
            sender = sender
        )

    private fun setupCompletedResponse(respFactory: (WireSigningRequest) -> Any) {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, WireSigningRequest::class.java)
            val future = CompletableFuture<WireSigningResponse>()
            future.complete(
                WireSigningResponse(
                    WireResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        Instant.now(),
                        req.context.memberId,
                        req.context.other
                    ), respFactory(req)
                )
            )
            future
        }
    }

    private fun validatePassedRequestContext(
        requests: KArgumentCaptor<WireSigningRequest>,
        nowBefore: Instant,
        nowAfter: Instant
    ) {
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertEquals(1, requests.firstValue.context.other.items.size)
        assertEquals("category", requests.firstValue.context.other.items[0].key)
        assertEquals(CryptoConsts.CryptoCategories.LEDGER, requests.firstValue.context.other.items[0].value)
    }
}