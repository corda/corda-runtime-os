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

    private val flowEvent = mock<FlowEvent>()
    private val requestClass = LedgerPersistenceRequest::class.java
    private val responseClass = FlowEvent::class.java

    private val target = LedgerPersistenceRequestProcessor(
        currentSandboxGroupContext,
        entitySandboxService,
        delegatedRequestHandlerSelector,
        responseFactory,
        requestClass,
        responseClass
    )

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

    @BeforeEach
    fun setup() {
        whenever(entitySandboxService.get(cordaHoldingIdentity, cpkHashes)).thenReturn(sandbox)
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(cordaHoldingIdentity)
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `requests routed to handlers to generate response messages`() {
        val request = createRequest("r1")
        val responseRecord = Record("", "1", flowEvent)
        val response = listOf(responseRecord)
        val handler = mock<RequestHandler>().apply { whenever(this.execute()).thenReturn(response) }
        whenever(delegatedRequestHandlerSelector.selectHandler(sandbox, request)).thenReturn(handler)

        val results = target.process(request)

        assertThat(results).isEqualTo(responseRecord.value)
    }

    @Test
    fun `failed request returns failure response back to the flow`() {
        val request = createRequest("r2")
        val failureResponseRecord = Record("", "3", FlowEvent())
        val response = IllegalStateException()
        val handler = mock<RequestHandler>().apply { whenever(this.execute()).thenThrow(response) }
        whenever(responseFactory.errorResponse(request.flowExternalEventContext, response))
            .thenReturn(failureResponseRecord)
        whenever(delegatedRequestHandlerSelector.selectHandler(sandbox, request)).thenReturn(handler)

        val results = target.process(request)

        assertThat(results).isEqualTo(failureResponseRecord.value)
    }


}

