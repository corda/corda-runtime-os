package net.corda.ledger.verification.processor.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class VerificationRequestProcessorTest {

    private companion object {
        const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val ALICE_X500_HOLDING_ID = HoldingIdentity(ALICE_X500, "group1")
        const val CPK_NAME = "test.cpk"
        const val CPK_VERSION = "1.0"
        const val CPK_CHECKSUM = "SHA-256:1212121212121212"
        const val SIGNER_SUMMARY_HASH = "SHA-256:3434343434343434"
    }

    private val verificationSandboxService = mock<VerificationSandboxService>()
    private val verificationRequestHandler = mock<VerificationRequestHandler>()
    private val responseFactory = mock<ExternalEventResponseFactory>()
    private val cordaHoldingIdentity = ALICE_X500_HOLDING_ID.toCorda()
    private val cpkSummaries = listOf(CordaPackageSummary(CPK_NAME, CPK_VERSION, SIGNER_SUMMARY_HASH, CPK_CHECKSUM))
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val flowEvent = mock<FlowEvent>()
    private val requestClass = TransactionVerificationRequest::class.java
    private val responseClass = FlowEvent::class.java

    private val verificationRequestProcessor = VerificationRequestProcessor(
        currentSandboxGroupContext,
        verificationSandboxService,
        verificationRequestHandler,
        responseFactory,
        requestClass,
        responseClass
    )

    private fun createRequest(requestId: String) =
        TransactionVerificationRequest().apply {
            timestamp = Instant.MIN
            flowExternalEventContext = ExternalEventContext(requestId, "f1", KeyValuePairList())
            holdingIdentity = ALICE_X500_HOLDING_ID
            cpkMetadata = listOf(
                CordaPackageSummary(CPK_NAME, CPK_VERSION, SIGNER_SUMMARY_HASH, CPK_CHECKSUM)
            )
        }

    @BeforeEach
    fun setup() {
        whenever(verificationSandboxService.get(cordaHoldingIdentity, cpkSummaries)).thenReturn(sandbox)
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(cordaHoldingIdentity)
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `successful response messages`() {
        val request = createRequest("r1")
        val responseRecord = Record("", "1", flowEvent)
        whenever(verificationRequestHandler.handleRequest(sandbox, request)).thenReturn(responseRecord)

        val results = verificationRequestProcessor.process(request)

        assertThat(results).isNotNull
        assertThat(results).isEqualTo(flowEvent)
    }

    @Test
    fun `failed request returns failure response back to the flow`() {
        val request = createRequest("r2")
        val failureResponseRecord = Record("", "3", FlowEvent())
        val response = IllegalStateException()

        whenever(verificationRequestHandler.handleRequest(sandbox, request)).thenThrow(response)
        whenever(responseFactory.transientError(request.flowExternalEventContext, response))
            .thenReturn(failureResponseRecord)

        val results = verificationRequestProcessor.process(request)

        assertThat(results).isNotNull
        assertThat(results).isEqualTo(failureResponseRecord.value)
    }
}
