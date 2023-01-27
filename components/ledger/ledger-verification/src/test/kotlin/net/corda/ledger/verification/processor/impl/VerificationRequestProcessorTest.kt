package net.corda.ledger.verification.processor.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.contract.verification.CordaPackageSummary
import net.corda.ledger.utxo.contract.verification.VerificationResult
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.ledger.utxo.contract.verification.VerifyContractsResponse
import net.corda.ledger.verification.exceptions.NotReadyException
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
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
    private val externalEventResponseFactory = mock<ExternalEventResponseFactory>()
    private val cordaHoldingIdentity = ALICE_X500_HOLDING_ID.toCorda()
    private val cpkChecksums = setOf(SecureHash.parse(CPK_CHECKSUM))
    private val sandbox = mock<SandboxGroupContext>()

    private val verificationRequestProcessor = VerificationRequestProcessor(
        verificationSandboxService,
        verificationRequestHandler,
        externalEventResponseFactory
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
    fun `state value should be of type VerifyContractsRequestRedelivery`() {
        assertThat(verificationRequestProcessor.stateValueClass).isEqualTo(VerifyContractsRequestRedelivery::class.java)
    }

    @Test
    fun `event value should be of type VerifyContractsRequest`() {
        assertThat(verificationRequestProcessor.eventValueClass).isEqualTo(VerifyContractsRequest::class.java)
    }

    @Test
    fun `successful response messages`() {
        val state = null
        val request = createRequest("r1")
        val event = Record("", "1", request)
        val response = VerifyContractsResponse(VerificationResult.VERIFIED, listOf())
        val responseRecord = Record("", "1", FlowEvent())
        whenever(verificationRequestHandler.handleRequest(sandbox, request)).thenReturn(response)
        whenever(externalEventResponseFactory.success(request.flowExternalEventContext, response))
            .thenReturn(responseRecord)

        val result = verificationRequestProcessor.onNext(state, event)

        val expectedResponse = StateAndEventProcessor.Response<VerifyContractsRequestRedelivery>(
            null,
            listOf(responseRecord)
        )
        assertThat(result).isEqualTo(expectedResponse)
    }

    @Test
    fun `failed request returns failure response back to the flow`() {
        val state = null
        val request = createRequest("r1")
        val event = Record("", "1", request)
        val failureResponseRecord = Record("", "3", FlowEvent())
        val requestResponse = IllegalStateException()
        whenever(verificationRequestHandler.handleRequest(sandbox, request)).thenThrow(requestResponse)
        whenever(externalEventResponseFactory.platformError(request.flowExternalEventContext, requestResponse))
            .thenReturn(failureResponseRecord)

        val result = verificationRequestProcessor.onNext(state, event)

        val expectedResponse = StateAndEventProcessor.Response<VerifyContractsRequestRedelivery>(
            null,
            listOf(failureResponseRecord)
        )
        assertThat(result).isEqualTo(expectedResponse)
    }

    @Test
    fun `failure to retrieve CPKs results with redelivery state`() {
        val state = null
        val request = createRequest("r1")
        val event = Record("", "1", request)
        whenever(verificationRequestHandler.handleRequest(sandbox, request)).doAnswer { throw NotReadyException("CPKs not found") }

        val result = verificationRequestProcessor.onNext(state, event)

        assertThat(result).isNotNull
        val resultState = result.updatedState
        assertThat(resultState).isNotNull
        assertThat(resultState!!.redeliveryNumber).isEqualTo(1)
        assertThat(resultState.scheduledDelivery).isAfter(resultState.timestamp)
        assertThat(resultState.request).isEqualTo(request)
        assertThat(result.responseEvents).isEmpty()
    }

    private fun createRequest(requestId: String): VerifyContractsRequest {
        return VerifyContractsRequest().apply {
            timestamp = Instant.MIN
            flowExternalEventContext = ExternalEventContext(requestId, "f1", KeyValuePairList())
            holdingIdentity = ALICE_X500_HOLDING_ID
            cpkMetadata = listOf(
                CordaPackageSummary("cpk1", "1.0", SIGNER_SUMMARY_HASH, CPK_CHECKSUM)
            )
        }
    }
}
