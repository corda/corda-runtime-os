package net.corda.crypto.impl.rpc

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
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import org.bouncycastle.jce.ECNamedCurveTable
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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertThat(requests.firstValue.context.other, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysEnsureWrappingKey::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should execute freshKey without external id request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val keyPair = generateKeyPair()
        setupCompletedResponse {
            WirePublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val nowBefore = Instant.now()
        val result = client.freshKey()
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysFreshKey::class.java))
        assertNull((requests.firstValue.request as WireFreshKeysFreshKey).externalId)
        assertEquals(keyPair.public, result)
    }

    @Test
    @Timeout(5)
    fun `Should execute freshKey with external id request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val keyPair = generateKeyPair()
        val externalId = UUID.randomUUID()
        setupCompletedResponse {
            WirePublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val nowBefore = Instant.now()
        val result = client.freshKey(externalId)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysFreshKey::class.java))
        assertEquals(externalId, UUID.fromString((requests.firstValue.request as WireFreshKeysFreshKey).externalId))
        assertEquals(keyPair.public, result)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val keyPair = generateKeyPair()
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = sign(keyPair.private, data)
        setupCompletedResponse {
            WireSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val nowBefore = Instant.now()
        val result = client.sign(keyPair.public, data)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysSign::class.java))
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), (requests.firstValue.request as WireFreshKeysSign).publicKey.array())
        assertArrayEquals(data, (requests.firstValue.request as WireFreshKeysSign).bytes .array())
        assertEquals(keyPair.public, result.by)
        assertArrayEquals(signature, result.bytes)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign with custom signature spec request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val keyPair = generateKeyPair()
        val spec = SignatureSpec("NONEwithECDSA", DigestAlgorithmName.SHA2_256)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = sign(keyPair.private, data)
        setupCompletedResponse {
            WireSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val nowBefore = Instant.now()
        val result = client.sign(keyPair.public, spec, data)
        val nowAfter = Instant.now()
        Mockito.verify(sender).sendRequest(requests.capture())
        assertEquals(memberId, requests.firstValue.context.memberId)
        assertEquals(requestingComponent, requests.firstValue.context.requestingComponent)
        assertThat(
            requests.firstValue.context.requestTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(nowBefore.epochSecond), lessThanOrEqualTo(nowAfter.epochSecond))
        )
        assertThat(requests.firstValue.context.other, empty())
        assertThat(requests.firstValue.request, instanceOf(WireFreshKeysSignWithSpec::class.java))
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), (requests.firstValue.request as WireFreshKeysSignWithSpec).publicKey.array())
        assertArrayEquals(data, (requests.firstValue.request as WireFreshKeysSignWithSpec).bytes .array())
        assertEquals("NONEwithECDSA", (requests.firstValue.request as WireFreshKeysSignWithSpec).signatureSpec.signatureName)
        assertEquals("SHA-256", (requests.firstValue.request as WireFreshKeysSignWithSpec).signatureSpec.customDigestName)
        assertEquals(keyPair.public, result.by)
        assertArrayEquals(signature, result.bytes)
    }

    @Test
    @Timeout(5)
    fun `Should execute filterMyKeys request`() {
        val requests = argumentCaptor<WireFreshKeysRequest>()
        val client = createClient()
        val myPublicKeys = listOf(
            generateKeyPair().public,
            generateKeyPair().public
        )
        val notMyKey = generateKeyPair().public
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
        assertThat(requests.firstValue.context.other, empty())
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
        Mockito.`when`(
            sender.sendRequest(any())
        ).thenAnswer {
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

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", schemeMetadata.providers["BC"])
        keyPairGenerator.initialize(ECNamedCurveTable.getParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }

    private fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA", schemeMetadata.providers["BC"])
        signature.initSign(privateKey, schemeMetadata.secureRandom)
        signature.update(data)
        return signature.sign()
    }
}