package net.corda.ledger.utxo.impl.token.selection.handlers

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ledger.utxo.token.selection.data.TokenForceClaimRelease
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.entities.TokenClaimCheckpointState
import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey
import net.corda.ledger.utxo.impl.token.selection.services.TokenClaimCheckpointService
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TokenClaimReleasePostProcessingHandlerTest {

    private val tokenClaimCheckpointService = mock<TokenClaimCheckpointService>()
    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val context = mock<FlowEventContext<Any>>().apply {
        whenever(checkpoint).thenReturn(flowCheckpoint)
    }

    private val claim1 = TokenClaimCheckpointState(
        "a1",
        PoolKey("h1", "t1", "i1", "n1", "s1")
    )
    private val claim2 = TokenClaimCheckpointState(
        "a2",
        PoolKey("h2", "t2", "i2", "n2", "s2")
    )

    private val expectedAvroPoolKey1 = TokenPoolCacheKey.newBuilder()
        .setShortHolderId("h1")
        .setTokenType("t1")
        .setIssuerHash("i1")
        .setNotaryX500Name("n1")
        .setSymbol("s1")
        .build()
    private val expectedPayload1 = TokenForceClaimRelease.newBuilder()
        .setClaimId("a1")
        .setPoolKey(expectedAvroPoolKey1)
        .build()
    private val expectedTokenPoolCacheEvent1 = TokenPoolCacheEvent.newBuilder()
        .setPoolKey(expectedAvroPoolKey1)
        .setPayload(expectedPayload1)
        .build()

    private val keyBytes1 = "key".toByteArray()
    private val dataBytes1 = "data".toByteArray()

    private val expectedRecord1 = Record(
        Schemas.Services.TOKEN_CACHE_EVENT,
        keyBytes1,
        dataBytes1
    )

    private val expectedAvroPoolKey2 = TokenPoolCacheKey.newBuilder()
        .setShortHolderId("h2")
        .setTokenType("t2")
        .setIssuerHash("i2")
        .setNotaryX500Name("n2")
        .setSymbol("s2")
        .build()
    private val expectedPayload2 = TokenForceClaimRelease.newBuilder()
        .setClaimId("a2")
        .setPoolKey(expectedAvroPoolKey2)
        .build()
    private val expectedTokenPoolCacheEvent2 = TokenPoolCacheEvent.newBuilder()
        .setPoolKey(expectedAvroPoolKey2)
        .setPayload(expectedPayload2)
        .build()
    private val keyBytes2 = "key2".toByteArray()
    private val dataBytes2 = "data2".toByteArray()

    private val expectedRecord2 = Record(
        Schemas.Services.TOKEN_CACHE_EVENT,
        keyBytes2,
        dataBytes2
    )

    private val serializer = mock<CordaAvroSerializer<Any>>().apply {
        whenever(serialize(eq(expectedAvroPoolKey1))).thenReturn(keyBytes1)
        whenever(serialize(eq(expectedTokenPoolCacheEvent1))).thenReturn(dataBytes1)
        whenever(serialize(eq(expectedAvroPoolKey2))).thenReturn(keyBytes2)
        whenever(serialize(eq(expectedTokenPoolCacheEvent2))).thenReturn(dataBytes2)
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>().apply {
        whenever(createAvroSerializer<Any>(any())).thenReturn(serializer)
    }

    private val target = TokenClaimReleasePostProcessingHandler(
        tokenClaimCheckpointService,
        cordaAvroSerializationFactory
    )

    @Test
    fun `Empty list returned for flows where flow not complete and not DLQ`() {
        whenever(tokenClaimCheckpointService.getTokenClaims(flowCheckpoint)).thenReturn(listOf(claim1, claim2))
        whenever(flowCheckpoint.isCompleted).thenReturn(false)
        whenever(context.sendToDlq).thenReturn(false)
        val results = target.postProcess(context)
        assertThat(results).isEmpty()
    }

    @Test
    fun `Empty list returned for a dlq flow no claims`() {
        whenever(tokenClaimCheckpointService.getTokenClaims(flowCheckpoint)).thenReturn(listOf())
        whenever(flowCheckpoint.isCompleted).thenReturn(false)
        whenever(context.sendToDlq).thenReturn(true)
        val results = target.postProcess(context)
        assertThat(results).isEmpty()
    }

    @Test
    fun `Empty list returned for a completed flow no claims`() {
        whenever(tokenClaimCheckpointService.getTokenClaims(flowCheckpoint)).thenReturn(listOf())
        whenever(flowCheckpoint.isCompleted).thenReturn(true)
        whenever(context.sendToDlq).thenReturn(false)
        val results = target.postProcess(context)
        assertThat(results).isEmpty()
    }

    @Test
    fun `Release event generated for each flow claim when flow completed`() {
        whenever(tokenClaimCheckpointService.getTokenClaims(flowCheckpoint)).thenReturn(listOf(claim1, claim2))
        whenever(flowCheckpoint.isCompleted).thenReturn(true)
        whenever(context.sendToDlq).thenReturn(false)
        val results = target.postProcess(context)
        assertThat(results).containsOnly(expectedRecord1, expectedRecord2)
    }

    @Test
    fun `Release event generated for each flow claim when DLQ`() {
        whenever(tokenClaimCheckpointService.getTokenClaims(flowCheckpoint)).thenReturn(listOf(claim1, claim2))
        whenever(flowCheckpoint.isCompleted).thenReturn(false)
        whenever(context.sendToDlq).thenReturn(true)
        val results = target.postProcess(context)
        assertThat(results).containsOnly(expectedRecord1, expectedRecord2)
    }
}
