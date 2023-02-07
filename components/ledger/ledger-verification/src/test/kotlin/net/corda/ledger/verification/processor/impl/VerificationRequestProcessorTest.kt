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
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.crypto.SecureHash
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
        const val CPK_CHECKSUM = "SHA-256:1212121212121212"
        const val SIGNER_SUMMARY_HASH = "SHA-256:3434343434343434"
    }

    private val verificationSandboxService = mock<VerificationSandboxService>()
    private val verificationRequestHandler = mock<VerificationRequestHandler>()
    private val responseFactory = mock<ExternalEventResponseFactory>()
    private val cordaHoldingIdentity = ALICE_X500_HOLDING_ID.toCorda()
    private val cpkChecksums = setOf(SecureHash.parse(CPK_CHECKSUM))
    private val sandbox = mock<SandboxGroupContext>()

    private val verificationRequestProcessor = VerificationRequestProcessor(
        verificationSandboxService,
        verificationRequestHandler,
        responseFactory
    )

    @BeforeEach
    fun setup() {
        whenever(verificationSandboxService.get(cordaHoldingIdentity, cpkChecksums)).thenReturn(sandbox)
    }

    @Test
    fun `key should be of type String`() {
        assertThat(verificationRequestProcessor.keyClass).isEqualTo(String::class.java)
    }

    @Test
    fun `value should be of type TransactionVerificationRequest`() {
        assertThat(verificationRequestProcessor.valueClass).isEqualTo(TransactionVerificationRequest::class.java)
    }

    @Test
    fun `successful response messages`() {
        val request1 = createRequest("r1")
        val requestRecord1 = Record("", "1", request1)
        val responseRecord1 = Record("", "1", "")
        whenever(verificationRequestHandler.handleRequest(sandbox, request1)).thenReturn(responseRecord1)

        val request2 = createRequest("r2")
        val requestRecord2 = Record("", "2", request2)
        val responseRecord2 = Record("", "2", "")
        whenever(verificationRequestHandler.handleRequest(sandbox, request2)).thenReturn(responseRecord2)

        val results = verificationRequestProcessor.onNext(listOf(requestRecord1, requestRecord2))

        assertThat(results).containsOnly(responseRecord1, responseRecord2)
    }

    @Test
    fun `failed request returns failure response back to the flow`() {
        // Success response for request 1
        val request1 = createRequest("r1")
        val requestRecord1 = Record("", "1", request1)
        val responseRecord1 = Record("", "1", "")
        whenever(verificationRequestHandler.handleRequest(sandbox, request1)).thenReturn(responseRecord1)

        // Failure response for request 2
        val request2 = createRequest("r2")
        val requestRecord2 = Record("", "2", request2)
        val failureResponseRecord = Record("", "3", FlowEvent())
        val request2Response = IllegalStateException()
        whenever(verificationRequestHandler.handleRequest(sandbox, request2)).thenThrow(request2Response)
        whenever(responseFactory.transientError(request2.flowExternalEventContext, request2Response))
            .thenReturn(failureResponseRecord)

        val results = verificationRequestProcessor.onNext(listOf(requestRecord1, requestRecord2))

        assertThat(results).containsOnly(responseRecord1, failureResponseRecord)
    }

    private fun createRequest(requestId: String) =
        TransactionVerificationRequest().apply {
            timestamp = Instant.MIN
            flowExternalEventContext = ExternalEventContext(requestId, "f1", KeyValuePairList())
            holdingIdentity = ALICE_X500_HOLDING_ID
            cpkMetadata = listOf(
                CordaPackageSummary("cpk1", "1.0", SIGNER_SUMMARY_HASH, CPK_CHECKSUM)
            )
        }
}
