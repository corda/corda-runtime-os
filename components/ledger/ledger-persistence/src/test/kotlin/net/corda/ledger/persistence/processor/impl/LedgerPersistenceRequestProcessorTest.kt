package net.corda.ledger.persistence.processor.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.ledger.persistence.ALICE_X500_HOLDING_ID
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestProcessor
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class LedgerPersistenceRequestProcessorTest {

    private val entitySandboxService = mock<EntitySandboxService>()
    private val delegatedRequestHandlerSelector = mock<DelegatedRequestHandlerSelector>()
    private val responseFactory = mock<ResponseFactory>()
    private val cordaHoldingIdentity = ALICE_X500_HOLDING_ID.toCorda()
    private val cpkHashes = mutableSetOf<SecureHash>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()

    private val target = LedgerPersistenceRequestProcessor(
        currentSandboxGroupContext,
        entitySandboxService,
        delegatedRequestHandlerSelector,
        responseFactory
    )

    @BeforeEach
    fun setup() {
        whenever(entitySandboxService.get(cordaHoldingIdentity, cpkHashes)).thenReturn(sandbox)
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(cordaHoldingIdentity)
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `key should be of type String`() {
        assertThat(target.keyClass).isEqualTo(String::class.java)
    }

    @Test
    fun `value should be of type LedgerPersistenceRequest`() {
        assertThat(target.valueClass).isEqualTo(LedgerPersistenceRequest::class.java)
    }

    @Test
    fun `requests routed to handlers to generate response messages`() {
        val request1 = createRequest("r1")
        val requestRecord1 = Record("", "1", request1)
        val responseRecord11 = Record("", "1", "")
        val responseRecord12 = Record("", "2", "")
        val request1Response = listOf(responseRecord11, responseRecord12)
        val handler1 = mock<RequestHandler>().apply { whenever(this.execute()).thenReturn(request1Response) }
        whenever(delegatedRequestHandlerSelector.selectHandler(sandbox, request1)).thenReturn(handler1)

        val request2 = createRequest("r2")
        val requestRecord2 = Record("", "2", request2)
        val responseRecord21 = Record("", "3", "")
        val request2Response = listOf(responseRecord21)
        val handler2 = mock<RequestHandler>().apply { whenever(this.execute()).thenReturn(request2Response) }
        whenever(delegatedRequestHandlerSelector.selectHandler(sandbox, request2)).thenReturn(handler2)

        val results = target.onNext(listOf(requestRecord1, requestRecord2))

        assertThat(results).containsOnly(responseRecord11, responseRecord12, responseRecord21)
    }

    @Test
    fun `failed request returns failure response back to the flow`() {
        // Success response for request 1
        val request1 = createRequest("r1")
        val requestRecord1 = Record("", "1", request1)
        val responseRecord1 = Record("", "1", "")
        val request1Response = listOf(responseRecord1)
        val handler1 = mock<RequestHandler>().apply { whenever(this.execute()).thenReturn(request1Response) }
        whenever(delegatedRequestHandlerSelector.selectHandler(sandbox, request1)).thenReturn(handler1)

        // Failure response for request 2
        val request2 = createRequest("r2")
        val requestRecord2 = Record("", "2", request2)
        val failureResponseRecord = Record("", "3", FlowEvent())
        val request2Response = IllegalStateException()
        val handler2 = mock<RequestHandler>().apply { whenever(this.execute()).thenThrow(request2Response) }
        whenever(responseFactory.errorResponse(request2.flowExternalEventContext, request2Response))
            .thenReturn(failureResponseRecord)
        whenever(delegatedRequestHandlerSelector.selectHandler(sandbox, request2)).thenReturn(handler2)

        val results = target.onNext(listOf(requestRecord1, requestRecord2))

        assertThat(results).containsOnly(responseRecord1, failureResponseRecord)
    }

    private fun createRequest(requestId: String): LedgerPersistenceRequest {
        return LedgerPersistenceRequest().apply {
            timestamp = Instant.MIN
            flowExternalEventContext = ExternalEventContext(
                requestId, "f1", KeyValuePairList(listOf(KeyValuePair("key", "value")))
            )
            ledgerType = LedgerTypes.CONSENSUAL
            request = FindTransaction()
            holdingIdentity = ALICE_X500_HOLDING_ID
        }
    }
}

