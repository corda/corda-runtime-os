 package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.config.impl.retrying
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullId
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_OP_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.REQUEST_TTL_KEY
import net.corda.crypto.flow.CryptoFlowOpsTransformer.Companion.RESPONSE_TOPIC
import net.corda.crypto.flow.impl.CryptoFlowOpsTransformerImpl
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.service.impl.SigningServiceImpl
import net.corda.crypto.service.impl.infra.ActResult
import net.corda.crypto.service.impl.infra.ActResultTimestamps
import net.corda.crypto.service.impl.infra.act
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePair
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
        val retryingConfig = configEvent.config.toCryptoConfig().retrying()
        processor = CryptoFlowOpsBusProcessor(mock(), signingService, externalEventResponseFactory, retryingConfig)
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

     /**
      * Results of a doFlowOperations experiment
      *
      * @param lookedUpSigningKeys - string form of signing keys looked up in each invocation
      * @param successfulFlowOpsResponses - flow ops responses which were successful
      * @param transformedResponse - transformed DTO form of the first successful response
      * @param capturedTenantIds - tenant IDs stored in flow resposnes
      * @param rawActResult - timing information and a list of raw records
      * @param recordKeys - the UUIds of each flow op request
      * @param rawFlowOpsResponses - uncast records for flow ops response capture
      * @param flowExternalEventContexts - the contexts prepared for each flow op
      */

     data class Results<R, S>(
         val lookedUpSigningKeys: List<List<String>>,
         val successfulFlowOpsResponses: List<R>,
         val transformedResponses: List<S>,
         val capturedTenantIds: List<String>,
         val rawActResult: ActResult<List<Record<*, *>>>,
         val recordKeys: List<String>,
         val rawFlowOpsResponses: List<FlowOpsResponse>,
         val flowExternalEventContexts: List<ExternalEventContext>,
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
         flowOpCallbacks: List<(CryptoFlowOpsTransformerImpl, ExternalEventContext) -> FlowOpsRequest?>,

         ): Results<R, S> {
         val indices = 0..(flowOpCallbacks.size - 1)
         val capturedTenantIds: MutableList<String> = mutableListOf()
         val lookedUpSigningKeys = mutableListOf<List<String>>() // the secure hashes passed into the signing service
         val recordKeys = flowOpCallbacks.map {
             UUID.randomUUID().toString()
         } // UUIDs for the flow op records that are passed into the crypto flow ops processor

         val flowExternalEventContexts =
             recordKeys.map { ExternalEventContext("request id", it, KeyValuePairList(emptyList())) }

         indices.map {
             whenever(
                 externalEventResponseFactory.success(
                     eq(flowExternalEventContexts.get(it)),
                     flowOpsResponseArgumentCaptor.capture()
                 )
             ).thenReturn(
                 Record(
                     Schemas.Flow.FLOW_EVENT_TOPIC,
                     flowExternalEventContexts.get(it).flowId,
                     FlowEvent()
                 )
             )
             whenever(
                 externalEventResponseFactory.platformError(
                     eq(flowExternalEventContexts.get(it)),
                     any<Throwable>()
                 )
             ).thenReturn(
                 Record(
                     Schemas.Flow.FLOW_EVENT_TOPIC,
                     flowExternalEventContexts.get(it).flowId,
                     FlowEvent()
                 )
             )
             whenever(
                 externalEventResponseFactory.transientError(
                     eq(flowExternalEventContexts.get(it)),
                     any<ExceptionEnvelope>()
                 )
             ).thenReturn(
                 Record(
                     Schemas.Flow.FLOW_EVENT_TOPIC,
                     flowExternalEventContexts.get(it).flowId,
                     FlowEvent()
                 )
             )
         }

         // capture what is passed in  to the signing service operations
         doAnswer {
             capturedTenantIds.add(it.getArgument(0))
             lookedUpSigningKeys.add(it.getArgument<List<SecureHash>>(1).map { it.toString() })
             myPublicKeys.map { mockSigningKeyInfo(it) }
         }.whenever(signingService).lookupSigningKeysByPublicKeyHashes(any(), any())
         doAnswer {
             capturedTenantIds.add(it.getArgument(0))
             DigitalSignatureWithKey(myPublicKeys.first(), byteArrayOf(42))
         }.whenever(signingService).sign(any(), any(), any(), any(), any())

         val transformer = buildTransformer()
         val flowOps = indices.map { flowOpCallbacks.get(it)(transformer, flowExternalEventContexts.get(it)) }

         // run the flows ops processor
         val result = act {
             processor.onNext(indices.map {
                 Record(
                     topic = eventTopic,
                     key = recordKeys.get(it),
                     value = flowOps.get(it)
                 )
             })
         }
         // if we got as many successes as ops we requested, we can check a 1:1 Mmapping between result keys and the record keys we put in,
         // otherwise we leave it to the caller to verify the mess.
         if (result.value != null && result.value.size == flowOpCallbacks.size) {
             indices.map { assertEquals(recordKeys.get(it), result.value.get(it).key) }
         }
         val successfulFlowOpsResponses =
             flowOpsResponseArgumentCaptor.allValues.map { assertResponseContext<P, R>(result, it) }

         val transformedResponses = flowOpsResponseArgumentCaptor.allValues.map {
             val x = transformer.transform(it)
             if (!(x is S)) throw IllegalArgumentException()
             x
         }

         return Results(
             lookedUpSigningKeys = lookedUpSigningKeys,
             successfulFlowOpsResponses = successfulFlowOpsResponses,
             transformedResponses = transformedResponses,
             capturedTenantIds = capturedTenantIds.toList(),
             rawActResult = result,
             recordKeys = recordKeys,
             rawFlowOpsResponses = flowOpsResponseArgumentCaptor.allValues,
             flowExternalEventContexts = flowExternalEventContexts
         )
     }

     @Suppress("UNCHECKED_CAST")
     @Test
     fun `Should process filter my keys query`() {
         val myPublicKeys = listOf(
             mockPublicKey(),
             mockPublicKey()
         )

         val notMyKey = mockPublicKey()

         val results = doFlowOperations<ByIdsFlowQuery, CryptoSigningKeys, List<PublicKey>>(
             myPublicKeys,
             listOf({ transformer, flowExternalEventContext ->
                 transformer.createFilterMyKeys(
                     tenantId,
                     listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
                     flowExternalEventContext
                 )
             })
         )
         assertEquals(1, results.lookedUpSigningKeys.size)
         val passedSecureHashes = results.lookedUpSigningKeys.first()
         assertEquals(3, passedSecureHashes.size)
         assertEquals(myPublicKeys[0].fullId(), passedSecureHashes[0])
         assertEquals(myPublicKeys[1].fullId(), passedSecureHashes[1])
         assertEquals(notMyKey.fullId(), passedSecureHashes[2])
         assertNotNull(results.successfulFlowOpsResponses.first().keys)
         assertEquals(2, results.successfulFlowOpsResponses.first().keys.size)
         assertTrue(results.successfulFlowOpsResponses.first().keys.any {
             it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[0]))
         })
         assertTrue(
             results.successfulFlowOpsResponses.first().keys.any {
                 it.publicKey.array().contentEquals(keyEncodingService.encodeAsByteArray(myPublicKeys[1]))
             }
         )
         assertEquals(2, results.transformedResponses.first().size)
         assertTrue(results.transformedResponses.first().any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
         assertTrue(results.transformedResponses.first().any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
         assertEquals(results.capturedTenantIds, listOf(tenantId))
     }


     @Test
     fun `Should process sign command`() {
         val publicKey = mockPublicKey()
         val data = UUID.randomUUID().toString().toByteArray()

         doFlowOperations<SignFlowCommand, CryptoSignatureWithKey, DigitalSignatureWithKey>(
             listOf(publicKey), listOf({ transformer, flowExternalEventContext ->
                 transformer.createSign(
                     UUID.randomUUID().toString(),
                     tenantId,
                     publicKey.encoded,
                     SignatureSpecs.EDDSA_ED25519,
                     data,
                     mapOf("key1" to "value1"),
                     flowExternalEventContext
                 )
             })
         )
    }


     //    @Suppress("UNCHECKED_CAST")
     @Test
     fun `Should process list with valid event and skip event without value`() {
         val myPublicKeys = listOf(
             mockPublicKey(),
             mockPublicKey()
         )
         val notMyKey = mockPublicKey()

         val r = doFlowOperations<ByIdsFlowQuery, CryptoSigningKeys, List<PublicKey>>(
             myPublicKeys, listOf(
                 { _, _ -> null },
                 { t, f -> t.createFilterMyKeys(tenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey), f) },
             )
         )
         assertEquals(listOf(tenantId), r.capturedTenantIds)
         assertEquals(3, r.lookedUpSigningKeys.first().size)

         // CryptoFlowOpsBusProcessor filters out null requests, since there's no information to send a response
         // so we should expect 1 output not 2 in this case
         assertEquals(1, r.transformedResponses.size)
         val transformed = r.transformedResponses.first()
         assertInstanceOf(List::class.java, transformed)
         assertEquals(2, transformed.size)
         assertTrue(transformed.any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
         assertTrue(transformed.any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
     }

     @Suppress("UNCHECKED_CAST")
     @Test
     fun `Should process list with valid event and return error for stale event`() {
         val myPublicKeys = listOf(
             mockPublicKey(),
             mockPublicKey()
         )
         val notMyKey = mockPublicKey()

         val r = doFlowOperations<ByIdsFlowQuery, CryptoSigningKeys, List<PublicKey>>(
             myPublicKeys, listOf(
                 { t, f ->
                     t.createFilterMyKeys(tenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey), f).apply {
                         context.other.items = context.other.items.filter {
                             it.key != REQUEST_TTL_KEY
                         }
                         context.other.items.add(KeyValuePair(REQUEST_TTL_KEY, "-1"))
                     }
                 },
                 { t, f -> t.createFilterMyKeys(tenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey), f) },
             )
         )
         assertEquals(2, r.rawActResult.value?.size)

         verify(externalEventResponseFactory).transientError(
             eq(r.flowExternalEventContexts.first()),
             any<ExceptionEnvelope>()
         )

         r.rawFlowOpsResponses.first().let { flowOpsResponse ->
             assertInstanceOf(CryptoSigningKeys::class.java, flowOpsResponse.response)
             val context1 = flowOpsResponse.context
             val response1 = flowOpsResponse.response as CryptoSigningKeys
             assertResponseContext<ByIdsFlowQuery>(r.rawActResult, context1, 123)
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
         assertEquals(1, r.capturedTenantIds.size)
         assertEquals(tenantId, r.capturedTenantIds[0])
         assertEquals(1, r.lookedUpSigningKeys.size)
         val passedList = r.lookedUpSigningKeys[0]
         assertEquals(3, passedList.size)
         assertEquals(myPublicKeys[0].fullId(), passedList[0])
         assertEquals(myPublicKeys[1].fullId(), passedList[1])
         assertEquals(notMyKey.fullId(), passedList[2])
         assertInstanceOf(List::class.java, r.transformedResponses.first())
         assertTrue(r.transformedResponses.first().any { it.encoded.contentEquals(myPublicKeys[0].encoded) })
         assertTrue(r.transformedResponses.first().any { it.encoded.contentEquals(myPublicKeys[1].encoded) })
     }

     @Test
     fun `Should process list with valid event and return error for failed event`() {
         val failingTenantId = UUID.randomUUID().toString()
         val myPublicKeys = listOf(
             mockPublicKey(),
             mockPublicKey()
         )
         val notMyKey = mockPublicKey()

         val r = doFlowOperations<ByIdsFlowQuery, CryptoSigningKeys, List<PublicKey>>(
             myPublicKeys, listOf(
                 { t, f ->
                     t.createFilterMyKeys(
                         failingTenantId,
                         listOf(myPublicKeys[0], myPublicKeys[1], notMyKey),
                         f
                     )
                 },
                 { t,f -> t.createFilterMyKeys( tenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey), f) }
             ))
         assertEquals(2, r.rawActResult.value?.size?:0)

         r.rawFlowOpsResponses.get(1).let { flowOpsResponse ->
             assertInstanceOf(CryptoSigningKeys::class.java, flowOpsResponse.response)
             val context1 = flowOpsResponse.context
             val response1 = flowOpsResponse.response as CryptoSigningKeys
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
         assertEquals(2, r.capturedTenantIds.size)
         assertEquals(failingTenantId, r.capturedTenantIds[0])
         assertEquals(tenantId, r.capturedTenantIds[1])
         assertEquals(2, r.lookedUpSigningKeys.size)
         val passedList0 = r.lookedUpSigningKeys[0]
         assertEquals(3, passedList0.size)
         assertEquals(myPublicKeys[0].fullId(), passedList0[0])
         assertEquals(myPublicKeys[1].fullId(), passedList0[1])
         assertEquals(notMyKey.fullId(), passedList0[2])
         val passedList1 = r.lookedUpSigningKeys[0]
         assertEquals(3, passedList1.size)
         assertEquals(myPublicKeys[0].fullId(), passedList1[0])
         assertEquals(myPublicKeys[1].fullId(), passedList1[1])
         assertEquals(notMyKey.fullId(), passedList1[2])
         assertInstanceOf(List::class.java, r.transformedResponses)
         val keys = r.transformedResponses.first()
         assertEquals(2, r.transformedResponses.first().size)
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