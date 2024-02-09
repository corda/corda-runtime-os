package net.corda.flow.maintenance

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CheckpointCleanupHandlerImplTest {

    private val config = SmartConfigImpl.empty()
        .withValue(FlowConfig.SESSION_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(1000))
        .withValue(FlowConfig.PROCESSING_FLOW_MAPPER_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(1000))
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val flowMessageFactory = mock<FlowMessageFactory>()

    @BeforeEach
    fun setup() {
        whenever(flowRecordFactory.createFlowStatusRecord(any())).thenReturn(mock())
        whenever(flowRecordFactory.createFlowMapperEventRecord(any(), any())).thenReturn(mock())
        whenever(flowSessionManager.getSessionErrorEventRecords(any(), any(),any())).thenReturn(listOf(mock()))
        whenever(flowMessageFactory.createFlowFailedStatusMessage(any(), any(), any())).thenReturn(mock())
        whenever(flowMessageFactory.createFlowKilledStatusMessage(any(), any())).thenReturn(mock())
    }

    @Test
    fun `when there are active sessions and the checkpoint is RPC started correct number of output records are generated`() {
        val checkpoint = setupCheckpoint(activeSessions = listOf("a", "b", "c"), rpcStarted = true)
        val handler = CheckpointCleanupHandlerImpl(flowRecordFactory, flowSessionManager, flowMessageFactory)
        val output = handler.cleanupCheckpoint(checkpoint, config, FlowFatalException("oops"))
        // There should be:
        // - 1 session error record, as the mock returns a single value
        // - 3 session cleanup records, as the mock is called three times for this.
        // - 1 status record
        // - 1 mapper cleanup record
        assertThat(output.size).isEqualTo(6)
        verify(flowMessageFactory).createFlowFailedStatusMessage(any(), any(), any())
        verify(checkpoint).putSessionStates(any())
        verify(checkpoint).markDeleted()
    }

    @Test
    fun `when there are active sessions and the checkpoint is P2P started correct number of output records are generated`() {
        val checkpoint = setupCheckpoint(activeSessions = listOf("a", "b", "c"), rpcStarted = false)
        val handler = CheckpointCleanupHandlerImpl(flowRecordFactory, flowSessionManager, flowMessageFactory)
        val output = handler.cleanupCheckpoint(checkpoint, config, FlowFatalException("oops"))
        // There should be:
        // - 1 session error record, as the mock returns a single value
        // - 3 session cleanup records, as the mock is called three times for this.
        // - 1 status record
        assertThat(output.size).isEqualTo(5)
        verify(flowMessageFactory).createFlowFailedStatusMessage(any(), any(), any())
        verify(checkpoint).putSessionStates(any())
        verify(checkpoint).markDeleted()
    }

    @Test
    fun `when there are no active sessions the checkpoint still errors any sessions with active errors`() {
        val checkpoint = setupCheckpoint(
            activeSessions = listOf(),
            rpcStarted = false,
            inactiveSessions = listOf("a", "b", "c")
        )
        val handler = CheckpointCleanupHandlerImpl(flowRecordFactory, flowSessionManager, flowMessageFactory)
        val output = handler.cleanupCheckpoint(checkpoint, config, FlowFatalException("oops"))
        // There should be:
        // - 1 session error record, as the mock returns a single value
        // - 3 session cleanup records, as the mock is called three times for this.
        // - 1 status record
        assertThat(output.size).isEqualTo(5)
        verify(flowMessageFactory).createFlowFailedStatusMessage(any(), any(), any())
        verify(checkpoint, never()).putSessionStates(any())
        verify(checkpoint).markDeleted()
    }

    @Test
    fun `when the flow is killed the right status message is created`() {
        val checkpoint = setupCheckpoint(activeSessions = listOf("a", "b", "c"), rpcStarted = true)
        val handler = CheckpointCleanupHandlerImpl(flowRecordFactory, flowSessionManager, flowMessageFactory)
        val output = handler.cleanupCheckpoint(checkpoint, config, FlowMarkedForKillException("oops"))
        // There should be:
        // - 1 session error record, as the mock returns a single value
        // - 3 session cleanup records, as the mock is called three times for this.
        // - 1 status record
        // - 1 mapper cleanup record
        assertThat(output.size).isEqualTo(6)
        verify(flowMessageFactory).createFlowKilledStatusMessage(any(), any())
        verify(checkpoint).putSessionStates(any())
    }

    private fun setupCheckpoint(
        activeSessions: List<String>,
        rpcStarted: Boolean,
        inactiveSessions: List<String> = listOf()
    ): FlowCheckpoint {
        val checkpoint = mock<FlowCheckpoint>()
        whenever(checkpoint.flowKey).thenReturn(FlowKey("foo", HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "baz")))
        val howStarted = if (rpcStarted) FlowInitiatorType.RPC else FlowInitiatorType.P2P
        val startContext = mock<FlowStartContext>().apply {
            whenever(initiatorType).thenReturn(howStarted)
        }
        whenever(checkpoint.flowStartContext).thenReturn(startContext)
        val sessions = activeSessions.map {
            mock<SessionState>().apply {
                whenever(status).thenReturn(SessionStateType.CONFIRMED)
                whenever(hasScheduledCleanup).thenReturn(false)
                whenever(sessionId).thenReturn(it)
            }
        } + inactiveSessions.map {
            mock<SessionState>().apply {
                whenever(status).thenReturn(SessionStateType.ERROR)
                whenever(hasScheduledCleanup).thenReturn(false)
                whenever(sessionId).thenReturn(it)
            }
        }
        whenever(checkpoint.sessions).thenReturn(sessions)
        return checkpoint
    }

}