package net.corda.applications.workers.smoketest.services

import net.corda.crypto.core.SecureHashImpl
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.identity.HoldingIdentity
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
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
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.utils.UniquenessAssertions
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests for the UniquenessChecker RPC service
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UniquenessChecker {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val serializationFactory = CordaAvroSerializationFactoryImpl(
        AvroSchemaRegistryImpl()
    )

    private val avroSerializer = serializationFactory.createAvroSerializer<UniquenessCheckRequestAvro> { }
    private val avroFlowEventDeserializer = serializationFactory.createAvroDeserializer({}, FlowEvent::class.java)
    private val avroUniquenessDeserializer = serializationFactory.createAvroDeserializer({}, UniquenessCheckResponseAvro::class.java)

    companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"
        const val NOTARY_SERVICE_X500 = "O=MyNotaryService, L=London, C=GB"
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
    fun `when call service with valid payload return idempotently`() {
        // TODO: construct path from constants (and add api/v5.1/ into it)
        val url = "${System.getProperty("uniquenessWorkerHealthHttp")}api/5.1/uniqueness-checker"
        // TODO: populate with real/useful data
        val serializedPayload = avroSerializer.serialize(payloadBuilder().build())

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        val responseBody: ByteArray = response.body()
        val responseEvent = avroFlowEventDeserializer.deserialize(responseBody)
        val resp = avroUniquenessDeserializer.deserialize((responseEvent?.payload as ExternalEventResponse).payload.array())
        // TODO: assert response
        assertSoftly {
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(responseEvent).isNotNull
            assertThat(resp).isNotNull
            UniquenessAssertions.assertStandardSuccessResponse(resp!!, testClock)
        }
    }

    private val testClock = AutoTickTestClock(Instant.MAX, Duration.ofSeconds(1))

    /**
     * Returns a random set of bytes
     */
    private fun randomBytes(): ByteArray {
        return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
    }

    /**
     * Returns a random secure hash of the specified algorithm
     */
    private fun randomSecureHash(algorithm: String = "SHA-256"): SecureHash {
        val digest = MessageDigest.getInstance(algorithm)
        return SecureHashImpl(digest.algorithm, digest.digest(randomBytes()))
    }

    private val defaultNotaryVNodeHoldingIdentity = HoldingIdentity(notaryX500, groupId)
    // We don't use Instant.MAX because this appears to cause a long overflow in Avro
    private val defaultTimeWindowUpperBound: Instant =
        LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    private fun payloadBuilder(txId: SecureHash = randomSecureHash())
            : UniquenessCheckRequestAvro.Builder =
        UniquenessCheckRequestAvro.newBuilder(
            UniquenessCheckRequestAvro(
                defaultNotaryVNodeHoldingIdentity,
                ExternalEventContext(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    KeyValuePairList(emptyList())
                ),
                txId.toString(),
                aliceX500,
                emptyList(),
                emptyList(),
                0,
                null,
                defaultTimeWindowUpperBound
            )
        )
}