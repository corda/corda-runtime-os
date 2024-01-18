package net.corda.applications.workers.smoketest.token.selection

import net.corda.applications.workers.smoketest.utils.PLATFORM_VERSION
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenForceClaimRelease
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.Timer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.scheduleAtFixedRate

/*
 * This will be removed in future PRs, for now it's useful for testing different optimizations.
 */
class SimpleHttpRestPerformanceTest {

    private companion object {
        const val HOLDING_ID = "A332E0C2F697"
        const val TOKEN_ISSUER_HASH = "SHA-256:EC4F2DBB3B140095550C9AFBBB69B5D6FD9E814B9DA82FAD0B34E9FCBE56F1CB"
        const val TOKEN_SYMBOL = "USD"
        const val TOKEN_TYPE = "TestUtxoState"
        const val TOKEN_NOTARY = "O=MyNotaryService, L=London, C=GB"
        val TOKEN_AMOUNT = BigDecimal.TEN
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val serializationFactory = CordaAvroSerializationFactoryImpl(
        AvroSchemaRegistryImpl()
    )

    private val avroSerializer = serializationFactory.createAvroSerializer<TokenPoolCacheEvent> { }
    private val avroFlowEventDeserializer =
        serializationFactory.createAvroDeserializer({}, FlowEvent::class.java)
    private val avroTokenClaimQueryResultDeserializer =
        serializationFactory.createAvroDeserializer({}, TokenClaimQueryResult::class.java)

    @Test
    @org.junit.jupiter.api.Disabled
    fun `60 second concurrent request test`() {
        val concurrentCount = 30
        val start = Instant.now().toEpochMilli()
        val concurrentClaims = ConcurrentHashMap<String, CompletableFuture<String>>()
        val concurrentReleases = ConcurrentHashMap<String, CompletableFuture<String>>()
        var completedClaims = 0
        var completedReleases = 0

        var running = true

        Timer("monitor").scheduleAtFixedRate(0, 500) {
            val diff = Instant.now().toEpochMilli() - start

            val claimRate = if (diff > 0) {
                (completedClaims * 1000) / diff
            } else {
                0
            }

            val releaseRate = if (diff > 0) {
                (completedClaims * 1000) / diff
            } else {
                0
            }

            println("${diff/1000} ${concurrentClaims.size} ${concurrentReleases.size} " +
                    "$completedClaims $completedReleases $claimRate $releaseRate ${claimRate+releaseRate}")
        }

        while (running) {
            if (concurrentClaims.size < concurrentCount) {
                val claimId = UUID.randomUUID().toString()
                val claim = getClaim(claimId)
                concurrentClaims[claimId] = claim
                claim.thenApply { completedClaimId ->
                    completedClaims++
                    concurrentClaims.remove(completedClaimId)
                    val release = forceClaimRelease(completedClaimId)
                    concurrentReleases[completedClaimId] = release
                    release.thenApply { releasedClaimId ->
                        concurrentReleases.remove(releasedClaimId)
                        completedReleases++
                    }
                }
            }
            running = Instant.now().toEpochMilli() < start + 60000
        }

        while (concurrentReleases.size > 0) {
            Thread.sleep(10)
        }
    }

    private fun forceClaimRelease(claimId: String): CompletableFuture<String> {
        val url = "${System.getProperty("tokenSelectionWorkerUrl")}api/$PLATFORM_VERSION/token-selection"

        val serializedPayload = avroSerializer.serialize(createReleasePayload(claimId))

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply { response ->

            assertThat(response.statusCode()).isEqualTo(200)
                .withFailMessage("status code on response: ${response.statusCode()} url: $url")

            val responseBody: ByteArray = response.body()
            assertThat(responseBody.size).isEqualTo(0)
            claimId
        }
    }

    private fun getClaim(claimId: String): CompletableFuture<String> {
        val url = "${System.getProperty("tokenSelectionWorkerUrl")}api/$PLATFORM_VERSION/token-selection"

        val serializedPayload = avroSerializer.serialize(createClaimPayload(claimId))

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply { response ->

            assertThat(response.statusCode()).isEqualTo(200)
                .withFailMessage("status code on response: ${response.statusCode()} url: $url")

            val responseBody: ByteArray = response.body()
            val responseFlowEvent = avroFlowEventDeserializer.deserialize(responseBody)
            val responseExternalEvent = responseFlowEvent?.payload as ExternalEventResponse
            val claimResponse =
                avroTokenClaimQueryResultDeserializer.deserialize(responseExternalEvent.payload.array())!!
            assertThat(claimResponse.claimedTokens.size).isGreaterThan(0)

            claimResponse.claimId
        }
    }

    private fun createClaimPayload(claimId: String): TokenPoolCacheEvent {
        val poolKey = TokenPoolCacheKey.newBuilder()
            .setShortHolderId(HOLDING_ID)
            .setTokenType(TOKEN_TYPE)
            .setIssuerHash(TOKEN_ISSUER_HASH)
            .setNotaryX500Name(TOKEN_NOTARY)
            .setSymbol(TOKEN_SYMBOL)
            .build()

        val externalEventContext = ExternalEventContext.newBuilder()
            .setFlowId("f1")
            .setRequestId(claimId)
            .setContextProperties(KeyValuePairList(listOf()))
            .build()

        val payload = TokenClaimQuery.newBuilder()
            .setRequestContext(externalEventContext)
            .setPoolKey(poolKey)
            .setTargetAmount(TOKEN_AMOUNT.toTokenAmount())
            .build()

        return TokenPoolCacheEvent.newBuilder()
            .setPoolKey(poolKey)
            .setPayload(payload)
            .build()
    }

    private fun createReleasePayload(claimId: String): TokenPoolCacheEvent {
        val poolKey = TokenPoolCacheKey.newBuilder()
            .setShortHolderId(HOLDING_ID)
            .setTokenType(TOKEN_TYPE)
            .setIssuerHash(TOKEN_ISSUER_HASH)
            .setNotaryX500Name(TOKEN_NOTARY)
            .setSymbol(TOKEN_SYMBOL)
            .build()

        val payload = TokenForceClaimRelease.newBuilder()
            .setPoolKey(poolKey)
            .setClaimId(claimId)
            .build()

        return TokenPoolCacheEvent.newBuilder()
            .setPoolKey(poolKey)
            .setPayload(payload)
            .build()
    }

    private fun BigDecimal.toTokenAmount() =
        TokenAmount.newBuilder()
            .setScale(this.scale())
            .setUnscaledValue(ByteBuffer.wrap(this.unscaledValue().toByteArray()))
            .build()
}