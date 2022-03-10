package net.corda.crypto.service.impl.flow

import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_OP_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_TTL_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_ERROR_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_TOPIC
import net.corda.crypto.service.impl.ActResult
import net.corda.crypto.service.impl.ActResultTimestamps
import net.corda.crypto.service.impl.act
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.flow.FilterMyKeysFlowQuery
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.SignFlowCommand
import net.corda.messaging.api.records.Record
import net.corda.v5.cipher.suite.KeyEncodingService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        ttl: Long
    ) {
        timestamps.assertThatIsBetween(context.requestTimestamp)
        assertEquals(tenantId, context.tenantId)
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
            CryptoPublicKeys(
                listOf(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = buildTransformer().createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoPublicKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) }
        )
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
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
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = buildTransformer().createFreshKey(tenantId, operationContext)
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
        val externalId = UUID.randomUUID()
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = buildTransformer().createFreshKey(tenantId, externalId, operationContext)
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
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey,
                        value = buildTransformer().createSign(
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
    }

    @Test
    fun `Should skip event without value`() {
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
            CryptoPublicKeys(
                listOf(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
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
                        value = buildTransformer().createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoPublicKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) }
        )
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
    }

    @Test
    fun `Should skip event with null response topic`() {
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
            CryptoPublicKeys(
                listOf(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = UUID.randomUUID().toString(),
                        value = buildTransformer().createFilterMyKeys(
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
                        value = buildTransformer().createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoPublicKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) }
        )
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
    }

    @Test
    fun `Should skip event with blank response topic`() {
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
            CryptoPublicKeys(
                listOf(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = UUID.randomUUID().toString(),
                        value = buildTransformer().createFilterMyKeys(
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
                        value = buildTransformer().createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey)
                        )
                    )
                )
            )
        }
        assertEquals(recordKey, result.value?.get(0)?.key)
        val response = assertResponseContext<FilterMyKeysFlowQuery, CryptoPublicKeys>(result)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) }
        )
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
    }

    @Test
    fun `Should return error for stale event`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        var passedTenantId = UUID.randomUUID().toString()
        var passedList = listOf<ByteBuffer>()
        val notMyKey = mockPublicKey()
        val recordKey0 = UUID.randomUUID().toString()
        val recordKey1 = UUID.randomUUID().toString()
        doAnswer {
            passedTenantId = it.getArgument(0)
            passedList = it.getArgument<Iterable<ByteBuffer>>(1).toList()
            CryptoPublicKeys(
                listOf(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
                )
            )
        }.whenever(cryptoOpsClient).filterMyKeysProxy(any(), any())
        val result = act {
            processor.onNext(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey0,
                        value = buildTransformer().createFilterMyKeys(
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
                        value = buildTransformer().createFilterMyKeys(
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
        assertTrue {
            (result.value[0].value as FlowOpsResponse).context.other.items.any {
                it.key == RESPONSE_ERROR_KEY && it.value.isNotBlank()
            }
        }
        assertInstanceOf(FlowOpsResponse::class.java, result.value[1].value)
        assertInstanceOf(CryptoPublicKeys::class.java, (result.value[1].value as FlowOpsResponse).response)
        val context = (result.value[1].value as FlowOpsResponse).context
        val response = (result.value[1].value as FlowOpsResponse).response as CryptoPublicKeys
        assertResponseContext<FilterMyKeysFlowQuery>(result, context, -1)
        assertNotNull(response.keys)
        assertEquals(2, response.keys.size)
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) }
        )
        assertTrue(
            response.keys.any { it.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1])) }
        )
        assertEquals(tenantId, passedTenantId)
        assertEquals(3, passedList.size)
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]), passedList[0].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]), passedList[1].array())
        assertArrayEquals(keyEncodingService.encodeAsByteArray(notMyKey), passedList[2].array())
    }
}