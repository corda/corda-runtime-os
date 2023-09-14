package net.corda.applications.workers.smoketest.services

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * Tests for the UniquenessChecker RPC service
 */
class UniquenessChecker {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()
    private val serializationFactory = CordaAvroSerializationFactoryImpl(
        AvroSchemaRegistryImpl()
    )

    private val avroSerializer = serializationFactory.createAvroSerializer<UniquenessCheckRequestAvro> { }
    private val avroDeserializer = serializationFactory.createAvroDeserializer({}, UniquenessCheckResponseAvro::class.java)

    @Test
    fun `when call service with valid payload return idempotently`() {
        // TODO: construct path from constants (and add api/v5.1/ into it)
        val url = "${System.getProperty("uniquenessWorkerHealthHttp")}uniqueness-checker"
        // TODO: populate with real/useful data
        val serializedPayload = avroSerializer.serialize(createPayload())

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        assertThat(response.statusCode()).isEqualTo(200)

        val responseBody: ByteArray = response.body()
        val responseEvent = avroDeserializer.deserialize(responseBody)

        // TODO: assert response
        assertThat(responseEvent).isNotNull
    }

    //TODO: draft a request builder using Ramzi's example
    private fun newRequestBuilder(txId: SecureHash = randomSecureHash())
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
                defaultOriginatorX500Name,
                emptyList(),
                emptyList(),
                0,
                null,
                defaultTimeWindowUpperBound
            )
        )

    private fun createPayload(): UniquenessCheckRequestAvro {
        return UniquenessCheckRequestAvro(
            HoldingIdentity(
                "CN=Notary-0fc246bf-b427-4652-befd-25d0e2375e9c, OU=Application, O=R3, L=London, C=GB",
                "1a2979b8-4791-4904-ad8f-26b0493179f8"
            ),
            ExternalEventContext(
                "c211dfae-badc-46f9-a19b-f3a69029e76f",
                "a25d65bc-1f8c-4644-b207-e16943ed3493",
                KeyValuePairList(
                    listOf(
                        KeyValuePair("corda.initiator.account", "account-zero"),
                        KeyValuePair("corda.initiator.client.id", "c7e370d0-1510-49af-84b5-fb7fe9f6f560"),
                        KeyValuePair(
                            "corda.initiator.cpiName",
                            "ledger-utxo-demo-app_0fc246bf-b427-4652-befd-25d0e2375e9c"
                        ),
                        KeyValuePair("corda.initiator.cpiVersion", "1.0.0.0-SNAPSHOT"),
                        KeyValuePair(
                            "corda.initiator.cpiSignerSummaryHash",
                            "E53DA5C67A637D42335808FA1534005281BAE7E49CCE8833213E58E0FDCA8B35"
                        ),
                        KeyValuePair(
                            "corda.initiator.cpiFileChecksum",
                            "5B472A8AEF77617B21FBF8D1985C4A006C6FCC1676EA19783DA8F1E349599083"
                        ),
                        KeyValuePair("corda.initiator.initialPlatformVersion", "50100"),
                        KeyValuePair("corda.initiator.initialSoftwareVersion", "5.1.0.0-SNAPSHOT"),
                        KeyValuePair("corda.initiator.flow.versioning", "RESET_VERSIONING_MARKER"),
                        KeyValuePair(
                            "corda.initiator.logged.transactionId",
                            "SHA-256D:8DFC45315F5B1E9D76B1F3A8F6DBC6275ED56F8B181C38A12155E4CBCAF439CC"
                        ),
                        KeyValuePair("corda.account", "account-zero"),
                        KeyValuePair(
                            "corda.cpiName",
                            "test-notary-server-cordapp_0fc246bf-b427-4652-befd-25d0e2375e9c"
                        ),
                        KeyValuePair("corda.cpiVersion", "1.0.0.0-SNAPSHOT"),
                        KeyValuePair(
                            "corda.cpiSignerSummaryHash",
                            "E53DA5C67A637D42335808FA1534005281BAE7E49CCE8833213E58E0FDCA8B35"
                        ),
                        KeyValuePair(
                            "corda.cpiFileChecksum",
                            "EF17E14F1919224DC1E754B15969159BAB68A0B8BD3D006F9D435835153FD15E"
                        ),
                        KeyValuePair("corda.initialPlatformVersion", "50100"),
                        KeyValuePair("corda.initialSoftwareVersion", "5.1.0.0-SNAPSHOT"),
                        KeyValuePair(
                            "corda.cpkFileChecksum.0",
                            "SHA-256:86D3A392EDE98BCB0FB0DD88D83CA28AEB408F9E7EBC4D34AFD4C8D49F1CA4F4"
                        ),
                        KeyValuePair(
                            "corda.cpkFileChecksum.1",
                            "SHA-256:3EEE03EA8747BC335D933A8D8A089A93EF2CFC5297789168E46B0615C2B4C298"
                        ),
                        KeyValuePair(
                            "corda.cpkFileChecksum.2",
                            "SHA-256:62F1637497E165F19D7AAFC78CF9F7E238CAE539D42BFD7274EC1C1FCD64775B"
                        ),
                    )
                )
            ),
            "SHA-256D:8DFC45315F5B1E9D76B1F3A8F6DBC6275ED56F8B181C38A12155E4CBCAF439CC",
            "CN=Alice-0fc246bf-b427-4652-befd-25d0e2375e9c, OU=Application, O=R3, L=London, C=GB",
            emptyList(),
            emptyList(),
            1,
            Instant.now(),
            Instant.now().plusSeconds(120)
        )
    }
}