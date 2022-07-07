package net.corda.flow.pipeline.handlers.waiting.persistence

import com.typesafe.config.ConfigValueFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.PipelineState
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseFailure
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.Error
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.PersistEntity
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.state.impl.FlowCheckpointImpl
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.time.Instant

class EntityResponseWaitingForHandlerTest {

    private companion object {
        const val persistTimeStampMilli = 1000000L
        const val resendWindow = 5000L
        const val sleepDuration = 10000L
        const val maxRetries = 3
        const val flowId = "flowId"
        const val requestId = "RequestId1"
        val bytes = ByteBuffer.wrap("bytes".toByteArray())
        val flowConfig: SmartConfig = SmartConfigImpl.empty()
            .withValue(FlowConfig.PERSISTENCE_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(resendWindow))
            .withValue(FlowConfig.PERSISTENCE_MAX_RETRIES, ConfigValueFactory.fromAnyRef(maxRetries))
            .withValue(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(sleepDuration))
    }

    private lateinit var persistRequest: EntityRequest
    private lateinit var findRequest: EntityRequest
    private lateinit var persistenceState: PersistenceState
    private lateinit var successResponseBytes: EntityResponse
    private lateinit var successResponseNull: EntityResponse
    private lateinit var errorResponseFatal: EntityResponse
    private lateinit var errorResponseNotReady: EntityResponse
    private lateinit var errorResponseVirtualNode: EntityResponse

    @BeforeEach
    fun setup() {
        persistRequest = EntityRequest.newBuilder()
            .setRequest(PersistEntity(bytes))
            .setTimestamp(Instant.ofEpochMilli(persistTimeStampMilli))
            .setFlowId(flowId)
            .setHoldingIdentity(HoldingIdentity("Alice", "Group1"))
            .build()

        findRequest = EntityRequest.newBuilder()
            .setRequest(FindEntity("class", bytes))
            .setTimestamp(Instant.ofEpochMilli(persistTimeStampMilli))
            .setFlowId(flowId)
            .setHoldingIdentity(HoldingIdentity("Alice", "Group1"))
            .build()

        persistenceState = PersistenceState.newBuilder()
            .setRequest(persistRequest)
            .setResponse(null)
            .setSendTimestamp(persistRequest.timestamp)
            .setRequestId(requestId)
            .build()

        successResponseBytes = EntityResponse.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .setResponseType(EntityResponseSuccess(bytes))
            .build()

        successResponseNull = EntityResponse.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .setResponseType(EntityResponseSuccess(null))
            .build()

        errorResponseFatal = EntityResponse.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .setResponseType(EntityResponseFailure(Error.FATAL, ExceptionEnvelope("", "")))
            .build()

        errorResponseNotReady = EntityResponse.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .setResponseType(EntityResponseFailure(Error.NOT_READY, ExceptionEnvelope("", "")))
            .build()

        errorResponseVirtualNode = EntityResponse.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .setResponseType(EntityResponseFailure(Error.VIRTUAL_NODE, ExceptionEnvelope("", "")))
            .build()
    }


    private val entityResponseWaitingForHandler = EntityResponseWaitingForHandler()

    @Test
    fun `response in persistence state is null`(){
        val context = stubFlowContext(EntityResponse(), null)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result).isEqualTo(FlowContinuation.Continue)
    }

    @Test
    fun `unexpected type for response`(){
        persistenceState.response = EntityResponse()
        val context = stubFlowContext(EntityResponse(), persistenceState)
        assertThrows<FlowFatalException> {
            entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        }
    }

    @Test
    fun `successful response for persist request`(){
        persistenceState.response = successResponseBytes
        val context = stubFlowContext(successResponseBytes, persistenceState)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result).isEqualTo(FlowContinuation.Run(Unit))
        assertThat(context.checkpoint.persistenceState).isNull()
    }

    @Test
    fun `successful response for find request`(){
        persistenceState.request = findRequest
        persistenceState.response = successResponseBytes
        val context = stubFlowContext(successResponseBytes, persistenceState)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result).isEqualTo(FlowContinuation.Run(bytes))
        assertThat(context.checkpoint.persistenceState).isNull()
    }

    @Test
    fun `fatal error response for request`(){
        persistenceState.response = errorResponseFatal
        val context = stubFlowContext(errorResponseFatal, persistenceState)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result::class.java).isEqualTo(FlowContinuation.Error::class.java)
        assertThat(context.checkpoint.persistenceState).isEqualTo(persistenceState)
    }

    @Test
    fun `not ready error response for request`(){
        persistenceState.response = errorResponseNotReady
        persistenceState.retries = maxRetries
        val context = stubFlowContext(errorResponseNotReady, persistenceState)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result).isEqualTo(FlowContinuation.Continue)
        assertThat(context.checkpoint.persistenceState?.retries).isEqualTo(maxRetries+1)
    }

    @Test
    fun `retriable error response for request`(){
        persistenceState.response = errorResponseVirtualNode
        val context = stubFlowContext(errorResponseVirtualNode, persistenceState)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result).isEqualTo(FlowContinuation.Continue)
        assertThat(context.checkpoint.persistenceState?.retries).isEqualTo(1)
    }

    @Test
    fun `retriable error response for request reached max retries`(){
        persistenceState.response = errorResponseVirtualNode
        persistenceState.retries = maxRetries
        val context = stubFlowContext(errorResponseVirtualNode, persistenceState)
        val result = entityResponseWaitingForHandler.runOrContinue(context, net.corda.data.flow.state.waiting.EntityResponse(requestId))
        assertThat(result::class.java).isEqualTo(FlowContinuation.Error::class.java)
        assertThat(context.checkpoint.persistenceState?.retries).isEqualTo(maxRetries)
    }

    private fun stubFlowContext(entityResponse: EntityResponse, persistenceState: PersistenceState? = null) :
            FlowEventContext<EntityResponse> {
        val flowCheckpoint = FlowCheckpointImpl(Checkpoint().apply { pipelineState = PipelineState() }, flowConfig) { Instant.now() }
        val startContext = FlowStartContext().apply {
            statusKey = FlowKey(FLOW_ID_1, BOB_X500_HOLDING_IDENTITY)
            identity = BOB_X500_HOLDING_IDENTITY
        }
        flowCheckpoint.initFlowState(startContext)
        flowCheckpoint.persistenceState = persistenceState
        return buildFlowEventContext(flowCheckpoint, entityResponse, flowConfig, emptyList(), flowId)
    }
}