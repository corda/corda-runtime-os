package net.corda.crypto.flow

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_OP_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_TTL_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_TOPIC
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignWithSpecFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.DigitalSignature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoFlowOpsTransformerTests {
    private lateinit var knownComponentName: String
    private lateinit var knownResponseTopic: String
    private lateinit var knownTenantId: String
    private lateinit var knownAlias: String
    private lateinit var knownOperationContext: Map<String, String>
    private lateinit var knownExternalId: String
    private lateinit var keyEncodingService: KeyEncodingService

    private fun buildTransformer(ttl: Long = 123): CryptoFlowOpsTransformer =
        CryptoFlowOpsTransformer(
            requestingComponent = knownComponentName,
            responseTopic = knownResponseTopic,
            keyEncodingService = keyEncodingService,
            requestValidityWindowSeconds = ttl
        )

    @BeforeEach
    fun setup() {
        knownComponentName = UUID.randomUUID().toString()
        knownResponseTopic = UUID.randomUUID().toString()
        knownTenantId = UUID.randomUUID().toString()
        knownAlias = UUID.randomUUID().toString()
        knownOperationContext = mapOf(
            UUID.randomUUID().toString() to UUID.randomUUID().toString()
        )
        knownExternalId = UUID.randomUUID().toString()
        keyEncodingService = mock {
            on { encodeAsByteArray(any()) } doAnswer {
                (it.getArgument(0) as PublicKey).encoded
            }
            on { decodePublicKey(any<ByteArray>()) } doAnswer { sc ->
                mock {
                    on { encoded } doAnswer {
                        sc.getArgument(0) as ByteArray
                    }
                }
            }
        }
    }

    private fun mockPublicKey(): PublicKey {
        val serialisedPublicKey = Random(Instant.now().toEpochMilli()).nextBytes(256)
        return mock {
            on { encoded } doReturn serialisedPublicKey
        }
    }

    private fun createResponse(
        response: Any,
        requestType: Class<*>,
        error: String? = null,
        ttl: Long = 300
    ): FlowOpsResponse =
        FlowOpsResponse(
            createWireResponseContext(requestType, error, ttl),
            response,
        )

    private fun createWireResponseContext(
        requestType: Class<*>,
        error: String?,
        ttl: Long = 300
    ): CryptoResponseContext {
        return CryptoResponseContext(
            knownComponentName,
            Instant.now(),
            UUID.randomUUID().toString(),
            Instant.now(),
            knownTenantId,
            KeyValuePairList(
                if(error != null) {
                    listOf(
                        KeyValuePair(REQUEST_OP_KEY, requestType.simpleName),
                        KeyValuePair(CryptoFlowOpsTransformer.RESPONSE_ERROR_KEY, error),
                        KeyValuePair(REQUEST_TTL_KEY, ttl.toString()),
                    )
                } else {
                    listOf(
                        KeyValuePair(REQUEST_OP_KEY, requestType.simpleName),
                        KeyValuePair(REQUEST_TTL_KEY, ttl.toString())
                    )
                }
            )
        )
    }

    private inline fun <reified REQUEST> assertRequestContext(result: ActResult<FlowOpsRequest>) {
        val context = result.value!!.context
        assertEquals(knownTenantId, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(knownComponentName, context.requestingComponent)
        assertEquals(3, context.other.items.size)
        assertTrue {
            context.other.items.firstOrNull {
                it.key == REQUEST_OP_KEY && it.value == REQUEST::class.java.simpleName
            } != null
        }
        assertTrue {
            context.other.items.firstOrNull {
                it.key == RESPONSE_TOPIC && it.value == knownResponseTopic
            } != null
        }
        assertTrue {
            context.other.items.firstOrNull {
                it.key == REQUEST_TTL_KEY && it.value == "123"
            } != null
        }
    }

    private fun assertOperationContext(expected: Map<String, String>, actual: KeyValuePairList) {
        assertNotNull(actual.items)
        assertEquals(expected.size, actual.items.size)
        expected.forEach {
            assertTrue { actual.items.firstOrNull { item -> item.key == it.key }?.value == it.value }
        }
    }

    @Test
    fun `Should create query to filter my keys`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        val notMyKey = mockPublicKey()
        val result = act {
            buildTransformer().createFilterMyKeys(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(FilterMyKeysFlowQuery::class.java, result.value.request)
        val query = result.value.request as FilterMyKeysFlowQuery
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(query.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(query.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(notMyKey)) })
        assertRequestContext<FilterMyKeysFlowQuery>(result)
    }

    @Test
    fun `Should create empty query to filter my keys`() {
        val result = act {
            buildTransformer().createFilterMyKeys(knownTenantId, listOf())
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(FilterMyKeysFlowQuery::class.java, result.value.request)
        val query = result.value.request as FilterMyKeysFlowQuery
        assertThat(query.keys).isEmpty()
        assertRequestContext<FilterMyKeysFlowQuery>(result)
    }

    @Test
    fun `Should create command to generate new fresh key without external id`() {
        val result = act {
            buildTransformer().createFreshKey(
                knownTenantId,
                CryptoConsts.Categories.CI,
                EDDSA_ED25519_CODE_NAME,
                knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(GenerateFreshKeyFlowCommand::class.java, result.value.request)
        val command = result.value.request as GenerateFreshKeyFlowCommand
        assertNull(command.externalId)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.schemeCodeName)
        assertRequestContext<GenerateFreshKeyFlowCommand>(result)
        assertOperationContext(knownOperationContext, command.context)
    }

    @Test
    fun `Should create command to generate new fresh key without external id and with empty operation context`() {
        val result = act {
            buildTransformer().createFreshKey(knownTenantId, CryptoConsts.Categories.CI, EDDSA_ED25519_CODE_NAME)
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(GenerateFreshKeyFlowCommand::class.java, result.value.request)
        val command = result.value.request as GenerateFreshKeyFlowCommand
        assertNull(command.externalId)
        assertEquals(CryptoConsts.Categories.CI, command.category)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.schemeCodeName)
        assertRequestContext<GenerateFreshKeyFlowCommand>(result)
        assertOperationContext(emptyMap(), command.context)
    }

    @Test
    fun `Should create command to generate new fresh key with external id`() {
        val result = act {
            buildTransformer().createFreshKey(
                knownTenantId,
                CryptoConsts.Categories.CI,
                knownExternalId,
                EDDSA_ED25519_CODE_NAME,
                knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(GenerateFreshKeyFlowCommand::class.java, result.value.request)
        val command = result.value.request as GenerateFreshKeyFlowCommand
        assertEquals(knownExternalId, command.externalId)
        assertEquals(CryptoConsts.Categories.CI, command.category)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.schemeCodeName)
        assertRequestContext<GenerateFreshKeyFlowCommand>(result)
        assertOperationContext(knownOperationContext, command.context)
    }

    @Test
    fun `Should create command to generate new fresh key with external id and with empty operation context`() {
        val result = act {
            buildTransformer().createFreshKey(
                knownTenantId,
                CryptoConsts.Categories.CI,
                knownExternalId,
                EDDSA_ED25519_CODE_NAME
            )
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(GenerateFreshKeyFlowCommand::class.java, result.value.request)
        val command = result.value.request as GenerateFreshKeyFlowCommand
        assertEquals(knownExternalId, command.externalId)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.schemeCodeName)
        assertRequestContext<GenerateFreshKeyFlowCommand>(result)
        assertOperationContext(emptyMap(), command.context)
    }

    @Test
    fun `Should create command to sign data`() {
        val publicKey = mockPublicKey()
        val data = "Hello World!".toByteArray()
        val result = act {
            buildTransformer().createSign(knownTenantId, publicKey, data, knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(SignFlowCommand::class.java, result.value.request)
        val command = result.value.request as SignFlowCommand
        assertArrayEquals(keyEncodingService.encodeAsByteArray(publicKey), command.publicKey.array())
        assertArrayEquals(data, command.bytes.array())
        assertRequestContext<SignFlowCommand>(result)
        assertOperationContext(knownOperationContext, command.context)
    }

    @Test
    fun `Should create command to sign data and with empty operation context`() {
        val publicKey = mockPublicKey()
        val data = "Hello World!".toByteArray()
        val result = act {
            buildTransformer().createSign(knownTenantId, publicKey, data)
        }
        assertNotNull(result.value)
        assertEquals(knownTenantId, result.value.context.tenantId)
        assertInstanceOf(SignFlowCommand::class.java, result.value.request)
        val command = result.value.request as SignFlowCommand
        assertArrayEquals(keyEncodingService.encodeAsByteArray(publicKey), command.publicKey.array())
        assertArrayEquals(data, command.bytes.array())
        assertRequestContext<SignFlowCommand>(result)
        assertOperationContext(emptyMap(), command.context)
    }

    @Test
    fun `Should infer filter my keys query from response`() {
        val response = createResponse(CryptoSigningKeys(), FilterMyKeysFlowQuery::class.java)
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, FilterMyKeysFlowQuery::class.java)
    }

    @Test
    fun `Should infer filter my keys query from response containing error`() {
        val response = createResponse(CryptoNoContentValue(), FilterMyKeysFlowQuery::class.java, "failed")
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, FilterMyKeysFlowQuery::class.java)
    }

    @Test
    fun `Should infer generate fresh key command from response`() {
        val response = createResponse(CryptoPublicKey(), GenerateFreshKeyFlowCommand::class.java)
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, GenerateFreshKeyFlowCommand::class.java)
    }

    @Test
    fun `Should infer generate fresh key command from response containing error`() {
        val response = createResponse(CryptoNoContentValue(), GenerateFreshKeyFlowCommand::class.java, "failed")
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, GenerateFreshKeyFlowCommand::class.java)
    }

    @Test
    fun `Should infer sign command from response`() {
        val response = createResponse(CryptoSignatureWithKey(), SignFlowCommand::class.java)
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, SignFlowCommand::class.java)
    }

    @Test
    fun `Should infer sign command from response containing error`() {
        val response = createResponse(CryptoNoContentValue(), SignFlowCommand::class.java, "failed")
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, SignFlowCommand::class.java)
    }

    @Test
    fun `Should infer sign command with explicit signature spec from response`() {
        val response = createResponse(CryptoSignatureWithKey(), SignWithSpecFlowCommand::class.java)
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, SignWithSpecFlowCommand::class.java)
    }

    @Test
    fun `Should infer sign command with explicit signature spec from response containing error`() {
        val response = createResponse(CryptoNoContentValue(), SignWithSpecFlowCommand::class.java, "failed")
        val result = buildTransformer().inferRequestType(response)
        assertEquals(result, SignWithSpecFlowCommand::class.java)
    }

    @Test
    fun `Should not infer request from unknown response`() {
        val response = createResponse(CryptoSignatureWithKey(), String::class.java)
        val result = buildTransformer().inferRequestType(response)
        assertNull(result)
    }

    @Test
    fun `Should not infer request from unknown response containing error`() {
        val response = createResponse(CryptoNoContentValue(), String::class.java, "failed")
        val result = buildTransformer().inferRequestType(response)
        assertNull(result)
    }

    @Test
    fun `Should throw IllegalArgumentException when transforming from response with unknown request`() {
        val response = createResponse(CryptoSignatureWithKey(), String::class.java)
        assertThrows<IllegalArgumentException> {
            buildTransformer().transform(response)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Should transform response to filter my keys query`() {
        val publicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        val response = createResponse(
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKeys[0])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    ),
                    CryptoSigningKey(
                        "id2",
                        "tenant",
                        "LEDGER",
                        "alias2",
                        "hsmAlias2",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            ),
            FilterMyKeysFlowQuery::class.java
        )
        val result = buildTransformer().transform(response)
        assertThat(result).isInstanceOf(List::class.java)
        val resultKeys = result as List<PublicKey>
        assertEquals(2, resultKeys.size)
        assertTrue(resultKeys.any { it.encoded.contentEquals(publicKeys[0].encoded) })
        assertTrue(resultKeys.any { it.encoded.contentEquals(publicKeys[1].encoded) })
    }

    @Test
    fun `Should throw IllegalStateException when transforming stale response to filter my keys query`() {
        val publicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        val response = createResponse(
            response = CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKeys[0])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    ),
                    CryptoSigningKey(
                        "id2",
                        "tenant",
                        "LEDGER",
                        "alias2",
                        "hsmAlias2",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            ),
            requestType = FilterMyKeysFlowQuery::class.java,
            error = null,
            ttl = -1
        )
        assertThrows<IllegalStateException> {
            buildTransformer(-1).transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming error response to filter my keys query`() {
        val response = createResponse(
            CryptoNoContentValue(),
            FilterMyKeysFlowQuery::class.java,
            "--failed--"
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence("--failed--")
    }

    @Test
    fun `Should throw IllegalStateException when transforming empty error response to filter my keys query`() {
        val response = createResponse(
            CryptoNoContentValue(),
            FilterMyKeysFlowQuery::class.java,
            ""
        )
        assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming unexpected response to filter my keys query`() {
        val response = createResponse(
            CryptoSignatureWithKey(),
            FilterMyKeysFlowQuery::class.java
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence(CryptoSignatureWithKey::class.java.name)
        assertThat(result.message).containsSequence(CryptoSigningKeys::class.java.name)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Should transform response to generate fresh key command`() {
        val publicKey = mockPublicKey()
        val response = createResponse(
            CryptoPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey))),
            GenerateFreshKeyFlowCommand::class.java
        )
        val result = buildTransformer().transform(response)
        assertThat(result).isInstanceOf(PublicKey::class.java)
        val resultKey = result as PublicKey
        assertArrayEquals(publicKey.encoded, resultKey.encoded)
    }

    @Test
    fun `Should throw IllegalStateException when transforming stale response to generate fresh key command`() {
        val publicKey = mockPublicKey()
        val response = createResponse(
            response = CryptoPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey))),
            requestType = GenerateFreshKeyFlowCommand::class.java,
            error = null,
            ttl = -1
        )
        assertThrows<IllegalStateException> {
            buildTransformer(-1).transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming error response to generate fresh key command`() {
        val response = createResponse(
            CryptoNoContentValue(),
            GenerateFreshKeyFlowCommand::class.java,
            "--failed--"
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence("--failed--")
    }

    @Test
    fun `Should throw IllegalStateException when transforming empty error response to generate fresh key command`() {
        val response = createResponse(
            CryptoNoContentValue(),
            GenerateFreshKeyFlowCommand::class.java,
            ""
        )
        assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming unexpected response to generate fresh key command`() {
        val response = createResponse(
            CryptoSignatureWithKey(),
            GenerateFreshKeyFlowCommand::class.java
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence(CryptoSignatureWithKey::class.java.name)
        assertThat(result.message).containsSequence(CryptoPublicKey::class.java.name)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Should transform response to sign command`() {
        val publicKey = mockPublicKey()
        val signature = "Hello World!".toByteArray()
        val response = createResponse(
            CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(signature)
            ),
            SignFlowCommand::class.java
        )
        val result = buildTransformer().transform(response)
        assertThat(result).isInstanceOf(DigitalSignature.WithKey::class.java)
        val resultSignature = result as DigitalSignature.WithKey
        assertArrayEquals(publicKey.encoded, resultSignature.by.encoded)
        assertArrayEquals(signature, resultSignature.bytes)
    }

    @Test
    fun `Should throw IllegalStateException when transforming stale response to sign command`() {
        val publicKey = mockPublicKey()
        val signature = "Hello World!".toByteArray()
        val response = createResponse(
            response = CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(signature)
            ),
            requestType = SignFlowCommand::class.java,
            error = null,
            ttl = -1
        )
        assertThrows<IllegalStateException> {
            buildTransformer(-1).transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming error response to sign command`() {
        val response = createResponse(
            CryptoNoContentValue(),
            SignFlowCommand::class.java,
            "--failed--"
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence("--failed--")
    }

    @Test
    fun `Should throw IllegalStateException when transforming empty error response to sign command`() {
        val response = createResponse(
            CryptoNoContentValue(),
            SignFlowCommand::class.java,
            ""
        )
        assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming unexpected response to sign command`() {
        val response = createResponse(
            CryptoSigningKeys(),
            SignFlowCommand::class.java
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence(CryptoSignatureWithKey::class.java.name)
        assertThat(result.message).containsSequence(CryptoSigningKeys::class.java.name)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Should transform response to sign command with signature spec`() {
        val publicKey = mockPublicKey()
        val signature = "Hello World!".toByteArray()
        val response = createResponse(
            CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(signature)
            ),
            SignWithSpecFlowCommand::class.java
        )
        val result = buildTransformer().transform(response)
        assertThat(result).isInstanceOf(DigitalSignature.WithKey::class.java)
        val resultSignature = result as DigitalSignature.WithKey
        assertArrayEquals(publicKey.encoded, resultSignature.by.encoded)
        assertArrayEquals(signature, resultSignature.bytes)
    }

    @Test
    fun `Should throw IllegalStateException when transforming error response to sign command with signature spec`() {
        val response = createResponse(
            CryptoNoContentValue(),
            SignWithSpecFlowCommand::class.java,
            "--failed--"
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence("--failed--")
    }

    @Test
    fun `Should throw IllegalStateException when transforming empty error response to sign command with signature spec`() {
        val response = createResponse(
            CryptoNoContentValue(),
            SignWithSpecFlowCommand::class.java,
            ""
        )
        assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
    }

    @Test
    fun `Should throw IllegalStateException when transforming unexpected response to sign command with signature spec`() {
        val response = createResponse(
            CryptoSigningKeys(),
            SignWithSpecFlowCommand::class.java
        )
        val result = assertThrows<IllegalStateException> {
            buildTransformer().transform(response)
        }
        assertThat(result.message).containsSequence(CryptoSignatureWithKey::class.java.name)
        assertThat(result.message).containsSequence(CryptoSigningKeys::class.java.name)
    }
}