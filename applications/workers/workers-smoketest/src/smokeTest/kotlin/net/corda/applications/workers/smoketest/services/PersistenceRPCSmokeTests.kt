package net.corda.applications.workers.smoketest.services

import net.corda.applications.workers.smoketest.utils.PLATFORM_VERSION
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Tests for the Persistence RPC service
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceRPCSmokeTests {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val serializationFactory = CordaAvroSerializationFactoryImpl(
        AvroSchemaRegistryImpl()
    )

    private val avroSerializer = serializationFactory.createAvroSerializer<LedgerPersistenceRequest> { }
    private val avroFlowEventDeserializer = serializationFactory.createAvroDeserializer({}, FlowEvent::class.java)

    companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"
        const val NOTARY_SERVICE_X500 = "O=MyNotaryService, L=London, C=GB"
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val testRunUniqueId = UUID.randomUUID()
    private val groupId = UUID.randomUUID().toString()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
    private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val charlieX500 = "CN=Charlie-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
    private val bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
    private val charlieHoldingId: String = getHoldingIdShortHash(charlieX500, groupId)
    private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, groupId)

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        charlieX500,
        notaryX500
    )

    @BeforeAll
    fun beforeAll() {
        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()

        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )
        conditionallyUploadCordaPackage(
            notaryCpiName,
            TEST_NOTARY_CPB_LOCATION,
            groupId,
            staticMemberList
        )

        val aliceActualHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
        val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlieX500, cpiName)
        val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
        assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)
        registerStaticMember(notaryHoldingId, NOTARY_SERVICE_X500)
    }

    @Test
    fun `RPC endpoint accepts a request and returns back a response`() {
        val url = "${System.getProperty("persistenceWorkerUrl")}api/$PLATFORM_VERSION/persistence"

        logger.info("persistence url: $url")
        val serializedPayload = avroSerializer.serialize(payloadBuilder().build())

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        assertThat(response.statusCode()).isEqualTo(200).withFailMessage("status code on response: ${response.statusCode()} url: $url")

        val responseBody: ByteArray = response.body()
        val responseEvent = avroFlowEventDeserializer.deserialize(responseBody)

        assertThat(responseEvent).isNotNull

        //val deserializedExternalEventResponse = avroUniquenessDeserializer.deserialize((responseEvent?.payload as ExternalEventResponse).payload.array())
        //assertThat(deserializedExternalEventResponse).isNotNull
        //TODO - persistence assertions
    }

    private val defaultNotaryVNodeHoldingIdentity = HoldingIdentity(notaryX500, groupId)
    private val payload = "payload"

    private fun payloadBuilder()
            : LedgerPersistenceRequest.Builder =
        LedgerPersistenceRequest.newBuilder(
            LedgerPersistenceRequest(
                Instant.MAX,
                defaultNotaryVNodeHoldingIdentity,
                LedgerTypes.CONSENSUAL,
                payload,
                ExternalEventContext(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    KeyValuePairList(emptyList())
                )
            )
        )
}