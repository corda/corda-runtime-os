package net.corda.crypto.service.impl.flow

import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_OP_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_TTL_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_ERROR_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_TOPIC
import net.corda.crypto.service.impl.infra.ActResult
import net.corda.crypto.service.impl.infra.ActResultTimestamps
import net.corda.crypto.service.impl.infra.act
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.messaging.api.records.Record
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoFlowOpsProcessorTests {
    private lateinit var tenantId: String
    private lateinit var componentName: String
    private lateinit var eventTopic: String
    private lateinit var responseTopic: String
    private lateinit var keyEncodingService: KeyEncodingService
    private lateinit var cryptoOpsClient: CryptoOpsProxyClient
    private lateinit var processor: CryptoFlowOpsProcessor

    private fun buildTransformer(ttl: Long = 123): CryptoFlowOpsTransformer =
        CryptoFlowOpsTransformer(
            requestingComponent = componentName,
            responseTopic = responseTopic,
            keyEncodingService = keyEncodingService,
            requestValidityWindowSeconds = ttl
        )

    private fun mockPublicKey(): PublicKey {
        val serialisedPublicKey = Random(Instant.now().toEpochMilli()).nextBytes(256)
        return mock {
            on { encoded } doReturn serialisedPublicKey
        }
    }

    private inline fun <reified REQUEST, reified RESPONSE> assertResponseContext(
        result: ActResult<List<Record<*, *>>>,
        ttl: Long = 123
    ): RESPONSE {
        assertNotNull(result.value)
        assertEquals(1, result.value.size)
        assertInstanceOf(FlowOpsResponse::class.java, result.value[0].value)
        assertInstanceOf(RESPONSE::class.java, (result.value[0].value as FlowOpsResponse).response)
        val context = (result.value[0].value as FlowOpsResponse).context
        val resp = (result.value[0].value as FlowOpsResponse).response as RESPONSE
        assertResponseContext<REQUEST>(result, context, ttl)
        return resp
    }

    private inline fun <reified REQUEST> assertResponseContext(
        timestamps: ActResultTimestamps,
        context: CryptoResponseContext,
        ttl: Long,
        expectedTenantId: String = tenantId
    ) {
        timestamps.assertThatIsBetween(context.requestTimestamp)
        assertEquals(expectedTenantId, context.tenantId)
        assertEquals(componentName, context.requestingComponent)
        assertTrue(context.other.items.size >= 3)
        assertTrue {
            context.other.items.firstOrNull {
                it.key == REQUEST_OP_KEY && it.value == REQUEST::class.java.simpleName
            } != null
        }
        assertTrue {
            context.other.items.firstOrNull {
                it.key == RESPONSE_TOPIC && it.value == responseTopic
            } != null
        }
        assertTrue {
            context.other.items.firstOrNull {
                it.key == REQUEST_TTL_KEY && it.value == ttl.toString()
            } != null
        }
    }

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        componentName = UUID.randomUUID().toString()
        eventTopic = UUID.randomUUID().toString()
        responseTopic = UUID.randomUUID().toString()
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
        cryptoOpsClient = mock()
        processor = CryptoFlowOpsProcessor(cryptoOpsClient)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process filter my keys query`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        var passedTenantId = UUID.randomUUID().toString()
        var passedList = listOf<ByteBuffer>()
        val notMyKey = mockPublicKey()
        val recordKey = UUID.randomUUID().toString()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedList = it.getArgument<Iterable<ByteBuffer>>(1).toList()
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
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
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoSigningKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
            }
        )
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
            }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) } )
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) } )
    }

    @Test
    fun `Should process generate new fresh key command without external id`() {
        val publicKey = mockPublicKey()
        var passedTenantId = UUID.randomUUID().toString()
        var passedContext = KeyValuePairList()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedContext = it.getArgument(1)
            CryptoPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)))
        }.whenever(cryptoOpsClient).freshKeyProxy(any(), any())
        val recordKey = UUID.randomUUID().toString()
        val operationContext = mapOf("key1" to "value1")
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createFreshKey(tenantId, operationContext)
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<GenerateFreshKeyFlowCommand, CryptoPublicKey>(result)
        assertArrayEquals(response.key.array(), keyEncodingService.encodeAsByteArray(publicKey))
        assertEquals(tenantId, passedTenantId)
        assertNotNull(passedContext.items)
        assertEquals(1, passedContext.items.size)
        assertTrue {
            passedContext.items[0].key == "key1" && passedContext.items[0].value == "value1"
        }
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(PublicKey::class.java, transformed)
        val key = transformed as PublicKey
        assertArrayEquals(publicKey.encoded, key.encoded)
    }

    @Test
    fun `Should process generate new fresh key command with external id`() {
        val publicKey = mockPublicKey()
        var passedTenantId = UUID.randomUUID().toString()
        var passedExternalId = UUID.randomUUID()
        var passedContext = KeyValuePairList()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedExternalId = it.getArgument(1)
            passedContext = it.getArgument(2)
            CryptoPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)))
        }.whenever(cryptoOpsClient).freshKeyProxy(any(), any(), any())
        val recordKey = UUID.randomUUID().toString()
        val operationContext = mapOf("key1" to "value1")
        val externalId = UUID.randomUUID().toString()
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createFreshKey(tenantId, externalId, operationContext)
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<GenerateFreshKeyFlowCommand, CryptoPublicKey>(result)
        assertArrayEquals(response.key.array(), keyEncodingService.encodeAsByteArray(publicKey))
        assertEquals(tenantId, passedTenantId)
        assertEquals(externalId, passedExternalId)
        assertNotNull(passedContext.items)
        assertEquals(1, passedContext.items.size)
        assertTrue {
            passedContext.items[0].key == "key1" && passedContext.items[0].value == "value1"
        }
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(PublicKey::class.java, transformed)
        val key = transformed as PublicKey
        assertArrayEquals(publicKey.encoded, key.encoded)
    }

    @Test
    fun `Should process sign command`() {
        val publicKey = mockPublicKey()
        val signature = UUID.randomUUID().toString().toByteArray()
        var passedTenantId = UUID.randomUUID().toString()
        var passedPublicKey = ByteBuffer.allocate(1)
        var passedData = ByteBuffer.allocate(1)
        var passedContext = KeyValuePairList()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedPublicKey = it.getArgument(1)
            passedData = it.getArgument(2)
            passedContext = it.getArgument(3)
            CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(signature)
            )
        }.whenever(cryptoOpsClient).signProxy(any(), any(), any(), any())
        val recordKey = UUID.randomUUID().toString()
        val data = UUID.randomUUID().toString().toByteArray()
        val operationContext = mapOf("key1" to "value1")
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createSign(
                            tenantId,
                            publicKey,
                            data,
                            operationContext
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<SignFlowCommand, CryptoSignatureWithKey>(result)
        assertArrayEquals(response.publicKey.array(), keyEncodingService.encodeAsByteArray(publicKey))
        assertArrayEquals(response.bytes.array(), signature)
        assertEquals(tenantId, passedTenantId)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(publicKey), passedPublicKey.array())
        assertArrayEquals(data, passedData.array())
        assertNotNull(passedContext.items)
        assertEquals(1, passedContext.items.size)
        assertTrue {
            passedContext.items[0].key == "key1" && passedContext.items[0].value == "value1"
        }
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(DigitalSignature.WithKey::class.java, transformed)
        val transformedSignature = transformed as DigitalSignature.WithKey
        assertArrayEquals(publicKey.encoded, transformedSignature.by.encoded)
        assertArrayEquals(signature, transformedSignature.bytes)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process list with valid event and skip event without value`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        var passedTenantId = UUID.randomUUID().toString()
        var passedList = listOf<ByteBuffer>()
        val notMyKey = mockPublicKey()
        val recordKey = UUID.randomUUID().toString()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedList = it.getArgument<Iterable<ByteBuffer>>(1).toList()
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
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
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = UUID.randomUUID().toString(),
                        value = null
                    ),
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoSigningKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
            }
        )
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
            }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) } )
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) } )
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process list with valid event and skip event with null response topic`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        var passedTenantId = UUID.randomUUID().toString()
        var passedList = listOf<ByteBuffer>()
        val notMyKey = mockPublicKey()
        val recordKey = UUID.randomUUID().toString()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedList = it.getArgument<Iterable<ByteBuffer>>(1).toList()
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
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
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = UUID.randomUUID().toString(),
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        ).apply {
                            context.other.items = context.other.items.filter {
                                it.key != RESPONSE_TOPIC
                            }
                        }
                    ),
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoSigningKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
            }
        )
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
            }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) } )
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) } )
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process list with valid event and skip event with blank response topic`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        var passedTenantId = UUID.randomUUID().toString()
        var passedList = listOf<ByteBuffer>()
        val notMyKey = mockPublicKey()
        val recordKey = UUID.randomUUID().toString()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedList = it.getArgument<Iterable<ByteBuffer>>(1).toList()
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
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
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = UUID.randomUUID().toString(),
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        ).apply {
                            context.other.items = context.other.items.filter {
                                it.key != RESPONSE_TOPIC
                            }
                            context.other.items.add(KeyValuePair(RESPONSE_TOPIC, "  "))
                        }
                    ),
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoSigningKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
            }
        )
        assertTrue(
            response.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
            }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
        val transformed = transformer.transform(result.value?.get(0)?.value as FlowOpsResponse)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) } )
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) } )
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process list with valid event and return error for stale event`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        val passedTenantIds = mutableListOf<String>()
        val passedLists = mutableListOf<List<ByteBuffer>>()
        val notMyKey = mockPublicKey()
        val recordKey0 = UUID.randomUUID().toString()
        val recordKey1 = UUID.randomUUID().toString()
        doAnswer {
            passedTenantIds.add(it.getArgument(0))
            passedLists.add(it.getArgument<Iterable<ByteBuffer>>(1).toList())
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
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
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey0,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        ).apply {
                            context.other.items = context.other.items.filter {
                                it.key != REQUEST_TTL_KEY
                            }
                            context.other.items.add(KeyValuePair(REQUEST_TTL_KEY, "-1"))
                        }
                    ),
                    Record(
                        topic = eventTopic,
                        key = recordKey1,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey0, result.value?.get(0)?.key)
        assertEquals(recordKey1, result.value?.get(1)?.key)
        assertNotNull(result.value)
        assertEquals(2, result.value.size)
        assertInstanceOf(FlowOpsResponse::class.java, result.value[0].value)
        assertInstanceOf(CryptoNoContentValue::class.java, (result.value[0].value as FlowOpsResponse).response)
        val context0 = (result.value[0].value as FlowOpsResponse).context
        assertResponseContext<FilterMyKeysFlowQuery>(result, context0, -1)
        assertTrue {
            context0.other.items.any {
                it.key == RESPONSE_ERROR_KEY && it.value.isNotBlank()
            }
        }
        assertInstanceOf(FlowOpsResponse::class.java, result.value[1].value)
        assertInstanceOf(CryptoSigningKeys::class.java, (result.value[1].value as FlowOpsResponse).response)
        val context1 = (result.value[1].value as FlowOpsResponse).context
        val response1 = (result.value[1].value as FlowOpsResponse).response as CryptoSigningKeys
        assertResponseContext<FilterMyKeysFlowQuery>(result, context1, 123)
        assertNotNull(response1.keys)
        assertEquals(2, response1.keys.size)
        assertTrue(
            response1.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
            }
        )
        assertTrue(
            response1.keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
            }
        )
        assertEquals(1, passedTenantIds.size)
        assertEquals(tenantId, passedTenantIds[0])
        assertEquals(1, passedLists.size)
        val passedList = passedLists[0]
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
        assertThrows<IllegalStateException> {
            transformer.transform(result.value[0].value as FlowOpsResponse)
        }
        val transformed = transformer.transform(result.value[1].value as FlowOpsResponse)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) } )
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) } )
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process list with valid event and return error for failed event`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        val passedTenantIds = mutableListOf<String>()
        val passedLists = mutableListOf<List<ByteBuffer>>()
        val notMyKey = mockPublicKey()
        val recordKey0 = UUID.randomUUID().toString()
        val recordKey1 = UUID.randomUUID().toString()
        val failingTenantId = UUID.randomUUID().toString()
        doAnswer {
            val tenantId = it.getArgument<String>(0)
            passedTenantIds.add(tenantId)
            passedLists.add(it.getArgument<Iterable<ByteBuffer>>(1).toList())
            if(tenantId == failingTenantId) {
                throw NotImplementedError()
            }
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        "id1",
                        "tenant",
                        "LEDGER",
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
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
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey0,
                        value = transformer.createFilterMyKeys(
                            failingTenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    ),
                    Record(
                        topic = eventTopic,
                        key = recordKey1,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey0, result.value?.get(0)?.key)
        assertEquals(recordKey1, result.value?.get(1)?.key)
        assertNotNull(result.value)
        assertEquals(2, result.value.size)
        assertInstanceOf(FlowOpsResponse::class.java, result.value[0].value)
        assertInstanceOf(CryptoNoContentValue::class.java, (result.value[0].value as FlowOpsResponse).response)
        val context0 = (result.value[0].value as FlowOpsResponse).context
        assertResponseContext<FilterMyKeysFlowQuery>(result, context0, 123, failingTenantId)
        assertTrue {
            context0.other.items.any {
                it.key == RESPONSE_ERROR_KEY && it.value.isNotBlank()
            }
        }
        assertInstanceOf(FlowOpsResponse::class.java, result.value[1].value)
        assertInstanceOf(CryptoSigningKeys::class.java, (result.value[1].value as FlowOpsResponse).response)
        val context1 = (result.value[1].value as FlowOpsResponse).context
        val response1 = (result.value[1].value as FlowOpsResponse).response as CryptoSigningKeys
        assertResponseContext<FilterMyKeysFlowQuery>(result, context1, 123, tenantId)
        assertNotNull(response1.keys)
        assertEquals(2, response1.keys.size)
        assertTrue(
            response1.keys.any { it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) }
        )
        assertTrue(
            response1.keys.any { it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) }
        )
        assertEquals(2, passedTenantIds.size)
        assertEquals(failingTenantId, passedTenantIds[0])
        assertEquals(tenantId, passedTenantIds[1])
        assertEquals(2, passedLists.size)
        val passedList0 = passedLists[0]
        assertEquals(3, passedList0.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList0[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList0[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList0[2].array())
        val passedList1 = passedLists[0]
        assertEquals(3, passedList1.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList1[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList1[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList1[2].array())
        assertThrows<IllegalStateException> {
            transformer.transform(result.value[0].value as FlowOpsResponse)
        }
        val transformed = transformer.transform(result.value[1].value as FlowOpsResponse)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) } )
        assertTrue( keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) } )
    }
}