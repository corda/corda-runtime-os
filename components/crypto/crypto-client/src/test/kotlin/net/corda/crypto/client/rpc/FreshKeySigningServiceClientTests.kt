package net.corda.crypto.client.rpc

import net.corda.crypto.client.generateKeyPair
import net.corda.crypto.client.sign
import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WirePublicKeys
import net.corda.data.crypto.wire.WireResponseContext
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysEnsureWrappingKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFilterMyKeys
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFreshKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSign
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSignWithSpec
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.empty
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

class FreshKeySigningServiceClientTests {
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

    private lateinit var sender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>

    @BeforeEach
    fun setupEach() {
        sender = mock()
    }

    @Test
    @Timeout(5)
    fun `Should execute ensureWrappingKey request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        setupCompletedResponse { WireNoContentValue() }
        val nowBefore = Instant.now()
        client.ensureWrappingKey()
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysEnsureWrappingKey::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should execute freshKey without external id request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        setupCompletedResponse {
            WirePublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.freshKey(context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysFreshKey::class.java))
        assertNull((requests.firstValue.request as WireFreshKeysFreshKey).externalId)
        assertEquals(2, (requests.firstValue.request as WireFreshKeysFreshKey).context.items.size)
        assertTrue((requests.firstValue.request as WireFreshKeysFreshKey).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysFreshKey).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result)
    }

    @Test
    @Timeout(5)
    fun `Should execute freshKey with external id request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val keyPair = generateKeyPair(schemeMetadata)
        val externalId = UUID.randomUUID()
        setupCompletedResponse {
            WirePublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val context = mapOf(
            "someId" to UUID.randomUUID().toString(),
            "reason" to "Hello World!"
        )
        val nowBefore = Instant.now()
        val result = client.freshKey(externalId, context)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysFreshKey::class.java))
        assertEquals(externalId, UUID.fromString((requests.firstValue.request as WireFreshKeysFreshKey).externalId))
        assertEquals(2, (requests.firstValue.request as WireFreshKeysFreshKey).context.items.size)
        assertTrue((requests.firstValue.request as WireFreshKeysFreshKey).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysFreshKey).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
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
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysSign::class.java))
        assertArrayEquals(
            schemeMetadata.encodeAsByteArray(keyPair.public),
            (requests.firstValue.request as WireFreshKeysSign).publicKey.array()
        )
        assertArrayEquals(data, (requests.firstValue.request as WireFreshKeysSign).bytes .array())
        assertEquals(2, (requests.firstValue.request as WireFreshKeysSign).context.items.size)
        assertTrue((requests.firstValue.request as WireFreshKeysSign).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysSign).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result.by)
        assertArrayEquals(signature, result.bytes)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign with custom signature spec request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
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
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysSignWithSpec::class.java))
        assertArrayEquals(
            schemeMetadata.encodeAsByteArray(keyPair.public),
            (requests.firstValue.request as WireFreshKeysSignWithSpec).publicKey.array()
        )
        assertArrayEquals(data, (requests.firstValue.request as WireFreshKeysSignWithSpec).bytes .array())
        assertEquals(
            "NONEwithECDSA",
            (requests.firstValue.request as WireFreshKeysSignWithSpec).signatureSpec.signatureName
        )
        assertEquals(
            "SHA-256",
            (requests.firstValue.request as WireFreshKeysSignWithSpec).signatureSpec.customDigestName
        )
        assertEquals(2, (requests.firstValue.request as WireFreshKeysSignWithSpec).context.items.size)
        assertTrue((requests.firstValue.request as WireFreshKeysSignWithSpec).context.items.any {
            it.key == "someId" && it.value == context["someId"]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysSignWithSpec).context.items.any {
            it.key == "reason" && it.value == context["reason"]
        })
        assertEquals(keyPair.public, result.by)
        assertArrayEquals(signature, result.bytes)
    }

    @Test
    @Timeout(5)
    fun `Should execute filterMyKeys request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val myPublicKeys = listOf(
            generateKeyPair(schemeMetadata).public,
            generateKeyPair(schemeMetadata).public
        )
        val notMyKey = generateKeyPair(schemeMetadata).public
        setupCompletedResponse {
            WirePublicKeys(
                myPublicKeys.map { ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it)) }
            )
        }
        val nowBefore = Instant.now()
        val result = client.filterMyKeys(listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysFilterMyKeys::class.java))
        assertEquals(3, (requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.size)
        assertTrue((requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.any {
            schemeMetadata.decodePublicKey(it.array()) == myPublicKeys[0]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.any {
            schemeMetadata.decodePublicKey(it.array()) == myPublicKeys[1]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.any {
            schemeMetadata.decodePublicKey(it.array()) == notMyKey
        })
        assertEquals(2, result.count())
        assertTrue(result.any { it == myPublicKeys[0] })
        assertTrue(result.any { it == myPublicKeys[1] })
    }

    @Test
    @Timeout(5)
    fun `Should execute filterMyKeys request when it returns empty list`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val notMyKeys = listOf(
            generateKeyPair(schemeMetadata).public,
            generateKeyPair(schemeMetadata).public
        )
        setupCompletedResponse {
            WirePublicKeys(emptyList())
        }
        val nowBefore = Instant.now()
        val result = client.filterMyKeys(notMyKeys)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysFilterMyKeys::class.java))
        assertEquals(2, (requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.size)
        assertTrue((requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.any {
            schemeMetadata.decodePublicKey(it.array()) == notMyKeys[0]
        })
        assertTrue((requests.firstValue.request as WireFreshKeysFilterMyKeys).keys.any {
            schemeMetadata.decodePublicKey(it.array()) == notMyKeys[1]
        })
        assertEquals(0, result.count())
    }

    @Test
    @Timeout(5)
    fun `Should fail when response context member id does not match the request`() {
        val client = createClient()
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, WireFreshKeysRequest::class.java)
            val future = CompletableFuture<WireFreshKeysResponse>()
            future.complete(
                WireFreshKeysResponse(
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
            client.ensureWrappingKey()
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
            val req = it.getArgument(0, WireFreshKeysRequest::class.java)
            val future = CompletableFuture<WireFreshKeysResponse>()
            future.complete(
                WireFreshKeysResponse(
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
            client.ensureWrappingKey()
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
            val req = it.getArgument(0, WireFreshKeysRequest::class.java)
            val future = CompletableFuture<WireFreshKeysResponse>()
            future.complete(
                WireFreshKeysResponse(
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
            client.ensureWrappingKey()
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
            client.ensureWrappingKey()
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
            client.ensureWrappingKey()
        }
        assertNotNull(exception.cause)
        assertSame(timeout2, exception.cause)
    }

    @Test
    @Timeout(5)
    fun `Should eventually succeed request after retry`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        whenever(
            sender.sendRequest(any())
        ).then { throw TimeoutException() }.then {
            val req = it.getArgument(0, WireFreshKeysRequest::class.java)
            val future = CompletableFuture<WireFreshKeysResponse>()
            future.complete(
                WireFreshKeysResponse(
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
        val nowBefore = Instant.now()
        client.ensureWrappingKey()
        val nowAfter = Instant.now()
        Mockito.verify(sender, times(2)).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other.items, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysEnsureWrappingKey::class.java))
    }

    private fun createClient(): FreshKeySigningServiceClient =
        FreshKeySigningServiceClient(
            memberId = memberId,
            requestingComponent = requestingComponent,
            clientTimeout = Duration.ofSeconds(15),
            clientRetries = 1,
            schemeMetadata = schemeMetadata,
            sender = sender
        )

    private fun setupCompletedResponse(respFactory: (WireFreshKeysRequest) -> Any) {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, WireFreshKeysRequest::class.java)
            val future = CompletableFuture<WireFreshKeysResponse>()
            future.complete(
                WireFreshKeysResponse(
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
}