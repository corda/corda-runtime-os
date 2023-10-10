package net.corda.entityprocessor.impl.internal

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityRequest
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepositoryImpl
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

internal class EntityRequestProcessorTest {

    private companion object {
        const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val ALICE_X500_HOLDING_ID = HoldingIdentity(ALICE_X500, "group1")
        const val CPK_NAME = "test.cpk"
        const val CPK_VERSION = "1.0"
        const val CPK_CHECKSUM = "SHA-256:1212121212121212"
        const val SIGNER_SUMMARY_HASH = "SHA-256:3434343434343434"
    }

    private val entitySandboxService = mock<EntitySandboxService>()
    private val requestsIdsRepository = mock<RequestsIdsRepositoryImpl>()
    private val persistenceServiceInternal = mock<PersistenceServiceInternal>()
    private val responseFactory = mock<ResponseFactory>()
    private val cordaHoldingIdentity = ALICE_X500_HOLDING_ID.toCorda()
    private val cpkSummaries = setOf(SecureHash(CPK_NAME, CPK_VERSION, SIGNER_SUMMARY_HASH, CPK_CHECKSUM))
    cpkFileHashes = cpiInfoReadService.getCpkFileHashes(virtualNodeInfo)

    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    private val entityRequestProcessor = EntityRequestProcessor(
        currentSandboxGroupContext,
        entitySandboxService,
        responseFactory,
        this::noOpPayloadCheck,
        requestsIdsRepository
    )

    @BeforeEach
    fun setup() {
        whenever(entitySandboxService.get(cordaHoldingIdentity, cpkSummaries)).thenReturn(sandbox)
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(cordaHoldingIdentity)
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)

        whenever(persistenceServiceInternal.deleteEntities())
        whenever(persistenceServiceInternal.persist())
        whenever(persistenceServiceInternal.find())
        whenever(persistenceServiceInternal.deleteEntitiesByIds())
        whenever(persistenceServiceInternal.findWithNamedQuery())
        whenever(persistenceServiceInternal.merge())
        whenever(persistenceServiceInternal.findAll())
    }

    @Test
    fun `key should be of type String`() {
        Assertions.assertThat(entityRequestProcessor.keyClass).isEqualTo(String::class.java)
    }

    @Test
    fun `value should be of type EntityRequest`() {
        Assertions.assertThat(entityRequestProcessor.valueClass).isEqualTo(TransactionVerificationRequest::class.java)
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

        Assertions.assertThat(results).containsOnly(responseRecord1, responseRecord2)
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

        Assertions.assertThat(results).containsOnly(responseRecord1, failureResponseRecord)
    }

    private fun createRequest(
        holdingId: net.corda.virtualnode.HoldingIdentity,
        entity: Any,
        externalEventContext: ExternalEventContext = EXTERNAL_EVENT_CONTEXT
    ): EntityRequest {
        logger.info("Entity Request - entity: ${entity.javaClass.simpleName} $entity")
        return EntityRequest(holdingId.toAvro(), entity, externalEventContext)
    }
}