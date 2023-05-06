 package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullId
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_OP_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_TTL_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_TOPIC
import net.corda.crypto.flow.impl.CryptoFlowOpsTransformerImpl
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.service.impl.SigningServiceImpl
import net.corda.crypto.service.impl.infra.ActResult
import net.corda.crypto.service.impl.infra.ActResultTimestamps
import net.corda.crypto.service.impl.infra.act
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.ByIdsFlowQuery
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

 class CryptoFlowOpsBusProcessorTests {
    companion object {
        private val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.CRYPTO_CONFIG),
            mapOf(ConfigKeys.CRYPTO_CONFIG to
                    SmartConfigFactory.createWithoutSecurityServices().create(
                        createDefaultCryptoConfig("pass", "salt")
                )
            )
        )
    }

    private lateinit var tenantId: String
    private lateinit var componentName: String
    private lateinit var eventTopic: String
    private lateinit var responseTopic: String
    private lateinit var keyEncodingService: KeyEncodingService
    private lateinit var cryptoOpsClient: CryptoOpsProxyClient
    private lateinit var signingService: SigningServiceImpl
    private lateinit var externalEventResponseFactory: ExternalEventResponseFactory
    private lateinit var processor: CryptoFlowOpsBusProcessor
    private lateinit var digestService: DigestService

    private val flowOpsResponseArgumentCaptor = argumentCaptor<FlowOpsResponse>()

    private fun buildTransformer(ttl: Long = 123): CryptoFlowOpsTransformerImpl =
        CryptoFlowOpsTransformerImpl(
            serializer = mock(),
            requestingComponent = componentName,
            responseTopic = responseTopic,
            keyEncodingService = keyEncodingService,
            digestService = digestService,
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
        flowOpsResponse: FlowOpsResponse,
        ttl: Long = 123
    ): RESPONSE {
        assertNotNull(result.value)
//        assertEquals(1, result.value?.size)
        assertInstanceOf(RESPONSE::class.java, flowOpsResponse.response)
        val context = flowOpsResponse.context
        val resp = flowOpsResponse.response as RESPONSE
        assertResponseContext<REQUEST>(result, context, ttl)
        return resp
    }

    private inline fun <reified REQUEST> assertResponseContext(
        timestamps: ActResultTimestamps,
        context: CryptoResponseContext,
        ttl: Long
    ) {
        timestamps.assertThatIsBetween(context.responseTimestamp)
        //timestamps.assertThatIsBetween(context.requestTimestamp) // not always (or not normally?) true, TODO - find some way to cover?
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
        externalEventResponseFactory = mock()
        val publicKeyMock = mock<PublicKey> {
        }
        val signatureMock = mock<DigitalSignatureWithKey> {
            on { by } doReturn publicKeyMock
            on { bytes } doReturn byteArrayOf(9, 0, 0, 0)
        }
        val schemeMetadataMock = mock<CipherSchemeMetadata> {
            on { decodePublicKey(any<ByteArray>()) } doReturn publicKeyMock
            on { encodeAsByteArray(any()) } doReturn byteArrayOf(42)
        }
        signingService = mock<SigningServiceImpl> {
            on { sign(any(), any(), any(), any(), any()) } doReturn signatureMock
            on { schemeMetadata } doReturn schemeMetadataMock
        }
        processor =
            CryptoFlowOpsBusProcessor(cryptoOpsClient, signingService, externalEventResponseFactory, configEvent)
        digestService = mock<DigestService>().also {
            fun capture() {
                val bytesCaptor = argumentCaptor<ByteArray>()
                whenever(it.hash(bytesCaptor.capture(), any())).thenAnswer {
                    val bytes = bytesCaptor.firstValue
                    SecureHashImpl(DigestAlgorithmName.SHA2_256.name, bytes.sha256Bytes()).also {
                        capture()
                    }
                }
            }
            capture()
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process filter my keys query`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )

        val notMyKey = mockPublicKey()

        val results =  doFlowOperations<ByIdsFlowQuery, CryptoSigningKeys,  List<PublicKey>>(myPublicKeys, listOf({
            transformer, flowExternalEventContext -> transformer.createFilterMyKeys(
                tenantId,
                listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
                flowExternalEventContext
            )
        }))
        assertEquals(1, results.lookedUpSigningKeys.size)
        val passedSecureHashes = results.lookedUpSigningKeys.first()
        assertEquals(3, passedSecureHashes.size)
        assertEquals(myPublicKeys[0].fullId(), passedSecureHashes[0])
        assertEquals(myPublicKeys[1].fullId(), passedSecureHashes[1])
        assertEquals(notMyKey.fullId(), passedSecureHashes[2])
        assertNotNull(results.successfulFlowOpsResponses.first().keys)
        assertEquals(2, results.successfulFlowOpsResponses.first().keys.size)
        assertTrue(results.successfulFlowOpsResponses.first().keys.any { it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(
            results.successfulFlowOpsResponses.first().keys.any {
                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
            }
        )
        assertEquals(2, results.transformedResponse.size)
        assertTrue(results.transformedResponse.any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
        assertTrue(results.transformedResponse.any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
        assertEquals(results.capturedTenantIds, listOf(tenantId))
    }

     data class Results<R, S>(
         val lookedUpSigningKeys: List<List<String>>,
         val successfulFlowOpsResponses: List<R>,
         val transformedResponse: S,
         val capturedTenantIds: List<String>
     )

    /** Run a flow operation in the mocked flow ops bus processor

     * @param P - type parameter for the flow os request
     * @param R - type parameter for the flow ops responses
     * @param S - type parameter for transformed flow ops responses
     * @param myPublicKeys - the set of public keys available from the underlying signing service
     * @param flowOpCallbacks - a list of callback to create the flow signing opeeration required, given a transformer and an event context
     *
     * @returns Results instance capturing data recorded during the flow operations
     */
    private inline fun <reified P, reified R, reified S> doFlowOperations(
        myPublicKeys: List<PublicKey>,
        flowOpCallbacks: List<(CryptoFlowOpsTransformerImpl, ExternalEventContext)->FlowOpsRequest>,

    ): Results<R, S> {
        val indices = 0..(flowOpCallbacks.size-1)
        val underlyingServiceCapturedTenantIds: MutableList<String> = mutableListOf()
        var passedSecureHashLists = mutableListOf<List<String>>() // the secure hashes passed into the signing service
        val recordKeys = flowOpCallbacks.map { UUID.randomUUID().toString() } // UUIDs for the flow op records that are passed into the crypto flow ops processor

        val flowExternalEventContexts = recordKeys.map { ExternalEventContext("request id", it, KeyValuePairList(emptyList())) }

        whenever(
            externalEventResponseFactory.success(
                eq(flowExternalEventContexts.first()),
                flowOpsResponseArgumentCaptor.capture()
            )
        ).thenReturn(
            Record(
                Schemas.Flow.FLOW_EVENT_TOPIC,
                flowExternalEventContexts.first().flowId,
                FlowEvent()
            )
        )
        whenever(
            externalEventResponseFactory.platformError(
                eq(flowExternalEventContexts.first()),
                any<Throwable>()
            )
        ).thenReturn(
            Record(
                Schemas.Flow.FLOW_EVENT_TOPIC,
                flowExternalEventContexts.first().flowId,
                FlowEvent()
            )
        )
        
        // capture what is passed in  to the signing service operations
        doAnswer {
            underlyingServiceCapturedTenantIds.add(it.getArgument(0))
            passedSecureHashLists.add(it.getArgument<List<SecureHash>>(1).map { it.toString() })
            myPublicKeys.map { mockSigningKeyInfo(it) }
        }.whenever(signingService).lookupSigningKeysByPublicKeyHashes(any(), any())
        doAnswer {
            underlyingServiceCapturedTenantIds.add(it.getArgument(0))
            DigitalSignatureWithKey(myPublicKeys.first(), byteArrayOf(42))
        }.whenever(signingService).sign(any(), any(), any(), any(), any())

        val transformer = buildTransformer()
        val flowOps = indices.map { flowOpCallbacks.get(it)(transformer, flowExternalEventContexts.get(it)) }

        // run the flows ops processor
        val result = act { processor.onNext(indices.map { Record(topic = eventTopic, key = recordKeys.get(it), value = flowOps.get(it)) }) }
        assertEquals(recordKeys.first(), result.value?.get(0)?.key)
        val successfulFlowOpsResponses = flowOpsResponseArgumentCaptor.allValues.map { assertResponseContext<P, R>(result, it) }

        val transformedResponse = transformer.transform(flowOpsResponseArgumentCaptor.firstValue)
        if (!(transformedResponse is S)) throw IllegalArgumentException()
        return Results(passedSecureHashLists, successfulFlowOpsResponses, transformedResponse, capturedTenantIds = underlyingServiceCapturedTenantIds.toList())
    }

    @Test
    fun `Should process sign command`() {
        val publicKey = mockPublicKey()
        val data = UUID.randomUUID().toString().toByteArray()

        doFlowOperations<SignFlowCommand, CryptoSignatureWithKey, DigitalSignatureWithKey>(listOf(publicKey), listOf( { transformer, flowExternalEventContext ->
            transformer.createSign(
                UUID.randomUUID().toString(),
                tenantId,
                publicKey.encoded,
                SignatureSpecs.EDDSA_ED25519,
                data,
                mapOf("key1" to "value1"),
                flowExternalEventContext
            )
        }))
    }


//    @Suppress("UNCHECKED_CAST")
//    @Test
//    fun `Should process list with valid event and skip event without value`() {
//        val myPublicKeys = listOf(
//            mockPublicKey(),
//            mockPublicKey()
//        )
//        var passedTenantId = UUID.randomUUID().toString()
//        var passedList = listOf<String>()
//        val notMyKey = mockPublicKey()
//        val recordKey = UUID.randomUUID().toString()
//        val flowExternalEventContext = ExternalEventContext("request id", recordKey, KeyValuePairList(emptyList()))
//
//        whenever(
//            externalEventResponseFactory.success(
//                eq(flowExternalEventContext),
//                flowOpsResponseArgumentCaptor.capture()
//            )
//        ).thenReturn(
//            Record(
//                Schemas.Flow.FLOW_EVENT_TOPIC,
//                flowExternalEventContext.flowId,
//                FlowEvent()
//            )
//        )
//
//        doAnswer {
//            passedTenantId = it.getArgument(0)
//            passedList = it.getArgument<SecureHashes>(1).hashes.map { avroSecureHash ->
//                SecureHashImpl(avroSecureHash.algorithm, avroSecureHash.bytes.array()).toString()
//            }
//            CryptoSigningKeys(
//                listOf(
//                    CryptoSigningKey(
//                        "id1",
//                        "tenant",
//                        "LEDGER",
//                        "alias1",
//                        "hsmAlias1",
//                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[0])),
//                        "FAKE",
//                        null,
//                        null,
//                        null,
//                        Instant.now()
//                    ),
//                    CryptoSigningKey(
//                        "id2",
//                        "tenant",
//                        "LEDGER",
//                        "alias2",
//                        "hsmAlias2",
//                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(myPublicKeys[1])),
//                        "FAKE",
//                        null,
//                        null,
//                        null,
//                        Instant.now()
//                    )
//                )
//            )
//        }.whenever(cryptoOpsClient).lookupKeysByFullIdsProxy(any(), any())
//        val transformer = buildTransformer()
//        val result = act {
//            processor.onNext(
//                listOf(
//                    Record(
//                        topic = eventTopic,
//                        key = UUID.randomUUID().toString(),
//                        value = null
//                    ),
//                    Record(
//                        topic = eventTopic,
//                        key = recordKey,
//                        value = transformer.createFilterMyKeys(
//                            tenantId,
//                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
//                            flowExternalEventContext
//                        )
//                    )
//                )
//            )
//        }
//        assertEquals(recordKey, result.value?.get(0)?.key)
//        val response = assertResponseContext<ByIdsFlowQuery, CryptoSigningKeys>(
//            result,
//            flowOpsResponseArgumentCaptor.firstValue
//        )
//        assertNotNull(response.keys)
//        assertEquals(2, response.keys.size)
//        assertTrue(
//            response.keys.any {
//                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
//            }
//        )
//        assertTrue(
//            response.keys.any {
//                it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
//            }
//        )
//        assertEquals(tenantId, passedTenantId)
//        assertEquals(3, passedList.size)
//        assertEquals(myPublicKeys[0].fullId(), passedList[0])
//        assertEquals(myPublicKeys[1].fullId(), passedList[1])
//        assertEquals(notMyKey.fullId(), passedList[2])
//        val transformed = transformer.transform(flowOpsResponseArgumentCaptor.firstValue)
//        assertInstanceOf(List::class.java, transformed)
//        val keys = transformed as List<PublicKey>
//        assertEquals(2, transformed.size)
//        assertTrue(keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
//        assertTrue(keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
//    }

//    @Suppress("UNCHECKED_CAST")
//    @Test
//    fun `Should process list with valid event and return error for stale event`() {
//        val myPublicKeys = listOf(
//            mockPublicKey(),
//            mockPublicKey()
//        )
//        val passedTenantIds = mutableListOf<String>()
//        val passedLists = mutableListOf<List<String>>()
//        val notMyKey = mockPublicKey()
//        val recordKey0 = UUID.randomUUID().toString()
//        val recordKey1 = UUID.randomUUID().toString()
//        val flowExternalEventContext0 = ExternalEventContext("request id", recordKey0, KeyValuePairList(emptyList()))
//        val flowExternalEventContext1 = ExternalEventContext("request id", recordKey1, KeyValuePairList(emptyList()))
//
//        whenever(
//            externalEventResponseFactory.transientError(
//                eq(flowExternalEventContext0),
//                any<ExceptionEnvelope>()
//            )
//        ).thenReturn(
//            Record(
//                Schemas.Flow.FLOW_EVENT_TOPIC,
//                flowExternalEventContext0.flowId,
//                FlowEvent()
//            )
//        )
//
//        whenever(
//            externalEventResponseFactory.success(
//                eq(flowExternalEventContext1),
//                flowOpsResponseArgumentCaptor.capture()
//            )
//        ).thenReturn(
//            Record(
//                Schemas.Flow.FLOW_EVENT_TOPIC,
//                flowExternalEventContext1.flowId,
//                FlowEvent()
//            )
//        )
//
//        doAnswer {
//            passedTenantIds.add(it.getArgument(0))
//            passedLists.add(it.getArgument<List<SecureHash>>(1).map { it.toString() })
//            myPublicKeys.map { mockSigningKeyInfo(it) }
//        }.whenever(cryptoOpsClient).lookupKeysByFullIdsProxy(any(), any())
//        val transformer = buildTransformer()
//        val result = act {
//            processor.onNext(
//                listOf(
//                    Record(
//                        topic = eventTopic,
//                        key = recordKey0,
//                        value = transformer.createFilterMyKeys(
//                            tenantId,
//                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
//                            flowExternalEventContext0
//                        ).apply {
//                            context.other.items = context.other.items.filter {
//                                it.key != REQUEST_TTL_KEY
//                            }
//                            context.other.items.add(KeyValuePair(REQUEST_TTL_KEY, "-1"))
//                        }
//                    ),
//                    Record(
//                        topic = eventTopic,
//                        key = recordKey1,
//                        value = transformer.createFilterMyKeys(
//                            tenantId,
//                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
//                            flowExternalEventContext1
//                        )
//                    )
//                )
//            )
//        }
//        assertEquals(recordKey0, result.value?.get(0)?.key)
//        assertEquals(recordKey1, result.value?.get(1)?.key)
//        assertNotNull(result.value)
//        assertEquals(2, result.value?.size)
//
//        verify(externalEventResponseFactory).transientError(eq(flowExternalEventContext0), any<ExceptionEnvelope>())
//
//        flowOpsResponseArgumentCaptor.firstValue.let { flowOpsResponse ->
//            assertInstanceOf(CryptoSigningKeys::class.java, flowOpsResponse.response)
//            val context1 = flowOpsResponse.context
//            val response1 = flowOpsResponse.response as CryptoSigningKeys
//            assertResponseContext<ByIdsFlowQuery>(result, context1, 123)
//            assertNotNull(response1.keys)
//            assertEquals(2, response1.keys.size)
//            assertTrue(
//                response1.keys.any {
//                    it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
//                }
//            )
//            assertTrue(
//                response1.keys.any {
//                    it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
//                }
//            )
//        }
//        assertEquals(1, passedTenantIds.size)
//        assertEquals(tenantId, passedTenantIds[0])
//        assertEquals(1, passedLists.size)
//        val passedList = passedLists[0]
//        assertEquals(3, passedList.size)
//        assertEquals(myPublicKeys[0].fullId(), passedList[0])
//        assertEquals(myPublicKeys[1].fullId(), passedList[1])
//        assertEquals(notMyKey.fullId(), passedList[2])
//        val transformed = transformer.transform(flowOpsResponseArgumentCaptor.firstValue)
//        assertInstanceOf(List::class.java, transformed)
//        val keys = transformed as List<PublicKey>
//        assertEquals(2, transformed.size)
//        assertTrue(keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
//        assertTrue(keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
//    }

     @Test
     fun `Should process list with valid event and return error for failed event with generic engine`() {
         val failingTenantId = UUID.randomUUID().toString()
         val myPublicKeys = listOf(
             mockPublicKey(),
             mockPublicKey()
         )
         val notMyKey = mockPublicKey()

         doFlowOperations<ByIdsFlowQuery, CryptoSigningKeys,  List<PublicKey>>(
             myPublicKeys, listOf(
                 { t,f -> t.createFilterMyKeys( failingTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey), f) },
                 { t,f -> t.createFilterMyKeys( tenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey), f) }
             ))
     }

     @Suppress("UNCHECKED_CAST")
    @Test
    fun `Should process list with valid event and return error for failed event`() {
        val myPublicKeys = listOf(
            mockPublicKey(),
            mockPublicKey()
        )
        val passedTenantIds = mutableListOf<String>()
        val passedLists = mutableListOf<List<String>>()
        val notMyKey = mockPublicKey()
        val recordKey0 = UUID.randomUUID().toString()
        val recordKey1 = UUID.randomUUID().toString()
        val flowExternalEventContext0 = ExternalEventContext("request id", recordKey0, KeyValuePairList(emptyList()))
        val flowExternalEventContext1 = ExternalEventContext("request id", recordKey1, KeyValuePairList(emptyList()))
        val failingTenantId = UUID.randomUUID().toString()

        whenever(
            externalEventResponseFactory.platformError(
                eq(flowExternalEventContext0),
                any<Throwable>()
            )
        ).thenReturn(
            Record(
                Schemas.Flow.FLOW_EVENT_TOPIC,
                flowExternalEventContext0.flowId,
                FlowEvent()
            )
        )
        whenever(
            externalEventResponseFactory.success(
                eq(flowExternalEventContext1),
                flowOpsResponseArgumentCaptor.capture()
            )
        ).thenReturn(
            Record(
                Schemas.Flow.FLOW_EVENT_TOPIC,
                flowExternalEventContext1.flowId,
                FlowEvent()
            )
        )

        doAnswer {
            val tenantId = it.getArgument<String>(0)
            passedTenantIds.add(tenantId)
            passedLists.add(it.getArgument<List<SecureHash>>(1).map { it.toString() })
            if (tenantId == failingTenantId) {
                throw NotImplementedError()
            }
            myPublicKeys.map { mockSigningKeyInfo(it) }
        }.whenever(signingService).lookupSigningKeysByPublicKeyHashes(any(), any())
        val transformer = buildTransformer()
        val result = act {
            processor.onNextSilent(
                listOf(
                    Record(
                        topic = eventTopic,
                        key = recordKey0,
                        value = transformer.createFilterMyKeys(
                            failingTenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
                            flowExternalEventContext0
                        )
                    ),
                    Record(
                        topic = eventTopic,
                        key = recordKey1,
                        value = transformer.createFilterMyKeys(
                            tenantId,
                            listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
                            flowExternalEventContext1
                        )
                    )
                )
            )
        }
        assertEquals(recordKey0, result.value?.get(0)?.key)
        assertEquals(recordKey1, result.value?.get(1)?.key)
        assertNotNull(result.value)
        assertEquals(2, result.value?.size)

        verify(externalEventResponseFactory).platformError(eq(flowExternalEventContext0), any<Throwable>())

        flowOpsResponseArgumentCaptor.firstValue.let { flowOpsResponse ->
            assertInstanceOf(CryptoSigningKeys::class.java, flowOpsResponse.response)
            val context1 = flowOpsResponse.context
            val response1 = flowOpsResponse.response as CryptoSigningKeys
            assertResponseContext<ByIdsFlowQuery>(result, context1, 123)
            assertEquals(context1.tenantId, tenantId)
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
        }
        assertEquals(2, passedTenantIds.size)
        assertEquals(failingTenantId, passedTenantIds[0])
        assertEquals(tenantId, passedTenantIds[1])
        assertEquals(2, passedLists.size)
        val passedList0 = passedLists[0]
        assertEquals(3, passedList0.size)
        assertEquals(myPublicKeys[0].fullId(), passedList0[0])
        assertEquals(myPublicKeys[1].fullId(), passedList0[1])
        assertEquals(notMyKey.fullId(), passedList0[2])
        val passedList1 = passedLists[0]
        assertEquals(3, passedList1.size)
        assertEquals(myPublicKeys[0].fullId(), passedList1[0])
        assertEquals(myPublicKeys[1].fullId(), passedList1[1])
        assertEquals(notMyKey.fullId(), passedList1[2])
        val transformed = transformer.transform(flowOpsResponseArgumentCaptor.firstValue)
        assertInstanceOf(List::class.java, transformed)
        val keys = transformed as List<PublicKey>
        assertEquals(2, transformed.size)
        assertTrue(keys.any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
        assertTrue(keys.any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
    }

    private fun mockSigningKeyInfo(key0: PublicKey) = mock<SigningKeyInfo> {
        on { id } doAnswer {
            mock() {
                on { value } doAnswer { "id1" }
            }
        }
        on { timestamp } doAnswer { Instant.now() }
        on { publicKey } doAnswer { keyEncodingService.encodeAsByteArray(key0) }
    }
}