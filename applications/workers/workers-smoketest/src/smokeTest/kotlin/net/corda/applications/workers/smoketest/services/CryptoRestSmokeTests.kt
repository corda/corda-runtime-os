package net.corda.applications.workers.smoketest.services

import net.corda.applications.workers.smoketest.utils.PLATFORM_VERSION
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.toAvro
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.queries.ByIdsFlowQuery
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.eclipse.jetty.http.HttpMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Tests for the Crypto REST service
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CryptoRestSmokeTests : ClusterReadiness by ClusterReadinessChecker() {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val serializationFactory = CordaAvroSerializationFactoryImpl(
        AvroSchemaRegistryImpl()
    )

    private val avroSerializer = serializationFactory.createAvroSerializer<FlowOpsRequest> { }
    private val avroFlowEventDeserializer = serializationFactory.createAvroDeserializer({}, FlowEvent::class.java)
    private val avroCryptoDeserializer = serializationFactory.createAvroDeserializer({}, FlowOpsResponse::class.java)

    companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"
    }

    private val testRunUniqueId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()
    private val flowId = UUID.randomUUID()
    private val groupId = UUID.randomUUID().toString()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)

    private val externalEventContext: ExternalEventContext = createExternalEventContext()
    private lateinit var cryptoRequestContext: CryptoRequestContext
    private lateinit var cryptoUrl: String

    private fun createExternalEventContext(): ExternalEventContext {
        val simpleContext = KeyValuePairList(
            listOf(
                KeyValuePair("Hello", "World!")
            )
        )

        return ExternalEventContext.newBuilder()
            .setContextProperties(simpleContext)
            .setRequestId(requestId.toString())
            .setFlowId(flowId.toString())
            .build()
    }

    private val staticMemberList = listOf(aliceX500)

    @BeforeAll
    fun beforeAll() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(2), Duration.ofMillis(100))

        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()

        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )
        val aliceActualHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        registerStaticMember(aliceHoldingId)

        cryptoUrl = "${System.getProperty("cryptoWorkerUrl")}api/$PLATFORM_VERSION/crypto"
    }

    @BeforeEach
    fun setup() {
        cryptoRequestContext = createRequestContext()
    }

    @Test
    fun `REST endpoint accepts a request and returns back a response`() {
        val serializedPayload = avroSerializer.serialize(generateByIdsFlowOpsRequest())

        val request = buildHttpRequest(cryptoUrl, serializedPayload, HttpMethod.POST)
        val response = sendRequestAndReturnValidResponse(request)

        val deserializedExternalEventResponse =
            avroCryptoDeserializer.deserialize(response.payload.array())

        assertThat(deserializedExternalEventResponse).isNotNull
        assertResponseContext(cryptoRequestContext, deserializedExternalEventResponse!!.context)
    }

    @Test
    fun `REST endpoint accepts a request and returns back an error response with 200 status`() {
        val serializedPayload = avroSerializer.serialize(generateByIdsFlowOpsRequest(returnError = true))

        val request = buildHttpRequest(cryptoUrl, serializedPayload, HttpMethod.POST)
        val response = sendRequestAndReturnValidResponse(request)

        assertThat(response.payload).isNull()
        assertThat(response.error).isNotNull()
    }

    @Test
    fun `REST endpoint does not accept request and returns back a 500 error`() {
        val serializedPayload = avroSerializer.serialize(generateByIdsFlowOpsRequest())

        val request = buildHttpRequest(cryptoUrl, serializedPayload, HttpMethod.PUT)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        assertThat(response.statusCode())
            .isEqualTo(404)
            .withFailMessage("status code on response: ${response.statusCode()} url: $cryptoUrl")
    }

    /**
     * Generate simple request to lookup for keys by their full key ids.
     * Lookup will return no items in the response.
     */
    private fun generateByIdsFlowOpsRequest(returnError: Boolean = false) : FlowOpsRequest {
        val secureHash = SecureHashImpl("algorithm", "12345678".toByteArray()).toAvro()
        val generateByIdsRequest = ByIdsFlowQuery(SecureHashes(listOf(secureHash)))

        if (returnError) {
            cryptoRequestContext.tenantId = UUID.randomUUID().toString()
        }

        return FlowOpsRequest.newBuilder()
            .setContext(cryptoRequestContext)
            .setRequest(generateByIdsRequest)
            .setFlowExternalEventContext(externalEventContext)
            .build()
    }

    private fun createRequestContext(): CryptoRequestContext = CryptoRequestContext(
        "test-component",
        Instant.now(),
        UUID.randomUUID().toString(),
        aliceHoldingId,
        KeyValuePairList(
            listOf(
                KeyValuePair("key1", "value1"),
                KeyValuePair("key2", "value2")
            )
        )
    )

    private fun assertResponseContext(expected: CryptoRequestContext, actual: CryptoResponseContext) {
        assertEquals(expected.tenantId, actual.tenantId)
        assertEquals(expected.requestId, actual.requestId)
        assertEquals(expected.requestingComponent, actual.requestingComponent)
        assertEquals(expected.requestTimestamp, actual.requestTimestamp)
        assertSoftly { softly ->
            softly.assertThat(actual.other.items.size).isEqualTo(expected.other.items.size)
            softly.assertThat(actual.other.items.containsAll(expected.other.items))
            softly.assertThat(expected.other.items.containsAll(actual.other.items))
        }
    }

    private fun buildHttpRequest(url: String, payload: ByteArray?, method: HttpMethod): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .method(method.toString(), HttpRequest.BodyPublishers.ofByteArray(payload))
            .build()
    }

    private fun sendRequestAndReturnValidResponse(request: HttpRequest): ExternalEventResponse {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        assertThat(response.statusCode())
            .isEqualTo(200)
            .withFailMessage("status code on response: ${response.statusCode()} url: $cryptoUrl")

        val responseBody: ByteArray = response.body()
        val responseEvent = avroFlowEventDeserializer.deserialize(responseBody)

        assertThat(responseEvent).isNotNull

        return responseEvent!!.payload as ExternalEventResponse
    }
}
