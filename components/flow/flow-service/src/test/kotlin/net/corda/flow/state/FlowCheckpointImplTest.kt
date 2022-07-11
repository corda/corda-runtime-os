package net.corda.flow.state

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.checkpoint.PipelineState
import net.corda.data.flow.state.checkpoint.RetryState
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.state.impl.FlowCheckpointImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.time.Instant

class FlowCheckpointImplTest {
    private val flowConfig = ConfigFactory.empty()
        .withValue(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(60000L))
        .withValue(FlowConfig.PROCESSING_MAX_RETRY_DELAY, ConfigValueFactory.fromAnyRef(60000L))
    private val smartFlowConfig = SmartConfigFactory.create(flowConfig).create(flowConfig)
    private val now = Instant.MIN

    private fun getMinimumCheckpoint(): Pair<Checkpoint, FlowCheckpointImpl> {
        val checkpoint = setupAvroCheckpoint()

        return checkpoint to createFlowCheckpoint(checkpoint)
    }

    private fun setupAvroCheckpoint(
        initialiseFlowState: Boolean = true,
        key: FlowKey = FlowKey(FLOW_ID_1, BOB_X500_HOLDING_IDENTITY),
        holdingIdentity: HoldingIdentity = BOB_X500_HOLDING_IDENTITY,
        stackItems: List<FlowStackItem> = listOf(),
        sessionStates: List<SessionState> = listOf(),
        newFiber: ByteBuffer = ByteBuffer.wrap(byteArrayOf()),
        suspendedOn: String = "foo",
        waitingFor: WaitingFor = WaitingFor(Wakeup()),
        maxFlowSleepDuration: Int = 10000,
        suspendCount: Int = 0,
        retryState: RetryState? = null,
        persistenceState: PersistenceState? = null
    ) : Checkpoint {
        val startContext = FlowStartContext().apply {
            statusKey = key
            identity = holdingIdentity
        }
        val newFlowState = if (initialiseFlowState) {
            FlowState().apply {
                flowStartContext = startContext
                flowStackItems = stackItems.toMutableList()
                sessions = sessionStates.toMutableList()
                fiber = newFiber
                this.suspendedOn = suspendedOn
                this.waitingFor = waitingFor
                this.suspendCount = suspendCount
                this.persistenceState = persistenceState
            }
        } else {
            null
        }
        val newPipelineState = PipelineState().apply {
            this.maxFlowSleepDuration = maxFlowSleepDuration
            this.retryState = retryState
        }
        return Checkpoint().apply {
            flowId = "F1"
            flowState = newFlowState
            pipelineState = newPipelineState
        }
    }

    private fun createFlowCheckpoint(checkpoint: Checkpoint, config: SmartConfig? = null): FlowCheckpointImpl {
        return FlowCheckpointImpl(checkpoint, config ?: smartFlowConfig) { now }
    }

    @Test
    fun `accessing checkpoint before initialisation should throw`() {
        val checkpoint = setupAvroCheckpoint(initialiseFlowState = false)
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThrows<IllegalStateException> { flowCheckpoint.flowStack }
        assertThrows<IllegalStateException> { flowCheckpoint.serializedFiber }
        assertThrows<IllegalStateException> { flowCheckpoint.getSessionState("id") }
        assertThrows<IllegalStateException> { flowCheckpoint.putSessionState(SessionState()) }
    }

    @Test
    fun `existing checkpoint - ensures flow stack items are initialised`() {
        val flowStackItem = FlowStackItem()
        val checkpoint = setupAvroCheckpoint(stackItems = listOf(flowStackItem))
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThat(flowCheckpoint.flowStack.peekFirst()).isNotNull
    }

    @Test
    fun `existing checkpoint - guard against duplicate sessions`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val session2 = SessionState().apply { sessionId = "S1" }
        val checkpoint = setupAvroCheckpoint(sessionStates = listOf(session1, session2))

        val error = assertThrows<IllegalStateException> { createFlowCheckpoint(checkpoint) }

        assertThat(error.message).isEqualTo("Invalid checkpoint, flow F1 has duplicate session for Session IDs = [S1]")
    }

    @Test
    fun `existing checkpoint - sets sessions`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val session2 = SessionState().apply { sessionId = "S2" }
        val checkpoint = setupAvroCheckpoint(sessionStates = listOf(session1, session2))

        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThat(flowCheckpoint.sessions).containsOnly(session1, session2)
    }

    @Test
    fun `existing checkpoint - sets flow id`() {
        val checkpoint = setupAvroCheckpoint()

        assertThat(createFlowCheckpoint(checkpoint).flowId).isEqualTo("F1")
    }

    @Test
    fun `existing checkpoint - sets flow key`() {
        val flowKey = FlowKey("R1", BOB_X500_HOLDING_IDENTITY)
        val checkpoint = setupAvroCheckpoint(key = flowKey)

        assertThat(createFlowCheckpoint(checkpoint).flowKey).isEqualTo(flowKey)
    }

    @Test
    fun `existing checkpoint - sets holding identity`() {
        val checkpoint = setupAvroCheckpoint(holdingIdentity = BOB_X500_HOLDING_IDENTITY)

        assertThat(createFlowCheckpoint(checkpoint).holdingIdentity).isEqualTo(BOB_X500_HOLDING_IDENTITY.toCorda())
    }

    @Test
    fun `existing checkpoint - sets flow start context`() {
        val context = FlowStartContext().apply {
            statusKey = FlowKey(FLOW_ID_1, BOB_X500_HOLDING_IDENTITY)
            identity = BOB_X500_HOLDING_IDENTITY
        }
        val checkpoint = setupAvroCheckpoint()

        assertThat(createFlowCheckpoint(checkpoint).flowStartContext).isEqualTo(context)
    }

    @Test
    fun `existing checkpoint - sets suspended on`() {
        val checkpoint = setupAvroCheckpoint(suspendedOn = "classA")

        assertThat(createFlowCheckpoint(checkpoint).suspendedOn).isEqualTo("classA")
    }

    @Test
    fun `existing checkpoint - sets waiting for`() {
        val newWaitingFor = WaitingFor(Wakeup())
        val checkpoint = setupAvroCheckpoint(waitingFor = newWaitingFor)

        assertThat(createFlowCheckpoint(checkpoint).waitingFor).isEqualTo(newWaitingFor)
    }

    @Test
    fun `existing checkpoint - sets serialised fiber`() {
        val newFiber = ByteBuffer.wrap("abc".toByteArray())
        val checkpoint = setupAvroCheckpoint(newFiber = newFiber)

        assertThat(createFlowCheckpoint(checkpoint).serializedFiber).isEqualTo(newFiber)
    }

    @Test
    fun `get session`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val session2 = SessionState().apply { sessionId = "S2" }
        val checkpoint = setupAvroCheckpoint(sessionStates = listOf(session1, session2))

        assertThat(createFlowCheckpoint(checkpoint).getSessionState("S2")).isEqualTo(session2)
    }

    @Test
    fun `put session`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val checkpoint = setupAvroCheckpoint()

        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        flowCheckpoint.putSessionState(session1)
        assertThat(flowCheckpoint.sessions).containsOnly(session1)
    }

    @Test
    fun `init checkpoint`() {
        val newFlowId = "F1"
        val flowKey = FlowKey("R1", BOB_X500_HOLDING_IDENTITY)
        val flowStartContext = FlowStartContext().apply {
            statusKey = flowKey
            identity = BOB_X500_HOLDING_IDENTITY
        }

        val flowCheckpoint = createFlowCheckpoint(setupAvroCheckpoint(initialiseFlowState = false))

        flowCheckpoint.initFlowState(flowStartContext)

        assertThat(flowCheckpoint.flowId).isEqualTo(newFlowId)
        assertThat(flowCheckpoint.flowKey).isEqualTo(flowKey)
        assertThat(flowCheckpoint.flowStartContext).isEqualTo(flowStartContext)
        assertThat(flowCheckpoint.holdingIdentity).isEqualTo(BOB_X500_HOLDING_IDENTITY.toCorda())
        assertThat(flowCheckpoint.suspendedOn).isNull()
        assertThat(flowCheckpoint.waitingFor).isNull()
        assertThat(flowCheckpoint.flowStack.size).isEqualTo(0)
        assertThat(flowCheckpoint.sessions.size).isEqualTo(0)
    }

    @Test
    fun `to avro returns updated checkpoint`() {
        val serializedFiber = ByteBuffer.wrap(byteArrayOf(1))
        val flow = NonInitiatingFlowExample()
        val session1 = SessionState().apply { sessionId = "S1" }
        val waitingFor = WaitingFor(Any())
        val checkpoint = setupAvroCheckpoint(maxFlowSleepDuration = 60000)
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.suspendedOn = "A"
        flowCheckpoint.waitingFor = waitingFor
        val flowStackItem = flowCheckpoint.flowStack.push(flow)
        flowCheckpoint.putSessionState(session1)
        flowCheckpoint.serializedFiber = serializedFiber

        val avroCheckpoint = flowCheckpoint.toAvro()!!

        assertThat(avroCheckpoint.flowState.suspendedOn).isEqualTo("A")
        assertThat(avroCheckpoint.flowState.waitingFor).isEqualTo(waitingFor)
        assertThat(avroCheckpoint.flowState.flowStackItems.first()).isEqualTo(flowStackItem)
        assertThat(avroCheckpoint.flowState.sessions.first()).isEqualTo(session1)
        assertThat(avroCheckpoint.flowState.fiber).isEqualTo(serializedFiber)
        assertThat(avroCheckpoint.pipelineState.maxFlowSleepDuration).isEqualTo(60000)

        flowCheckpoint.markDeleted()
        assertThat(flowCheckpoint.toAvro()).isNull()
    }

    @Test
    fun `mark delete returns null from to avro`() {
        val checkpoint = setupAvroCheckpoint()

        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.markDeleted()
        assertThat(flowCheckpoint.toAvro()).isNull()
    }

    @Test
    fun `flow stack - peek first throws if stack empty`() {
        val checkpoint = setupAvroCheckpoint()
        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        assertThrows<IllegalStateException> { flowCheckpoint.flowStack.peekFirst() }
    }

    @Test
    fun `flow stack - peek first returns first item on the stack`() {
        val item1 = FlowStackItem()
        val item2 = FlowStackItem()

        val checkpoint = setupAvroCheckpoint(stackItems = listOf(item1, item2))
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThat(flowCheckpoint.flowStack.peekFirst()).isEqualTo(item1)
    }

    @Test
    fun `flow stack - pop removes and returns top item`() {
        val flowStackItem0 = FlowStackItem()
        val flowStackItem1 = FlowStackItem()
        val checkpoint = setupAvroCheckpoint(stackItems = listOf(flowStackItem0, flowStackItem1))

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.pop()).isEqualTo(flowStackItem1)
        assertThat(service.pop()).isEqualTo(flowStackItem0)

    }

    @Test
    fun `flow stack - pop removes and returns null when stack empty`() {
        val flowStackItem0 = FlowStackItem()
        val checkpoint = setupAvroCheckpoint(stackItems = listOf(flowStackItem0))

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.pop()).isEqualTo(flowStackItem0)
        assertThat(service.pop()).isNull()
    }

    @Test
    fun `flow stack - peek returns top item`() {
        val flowStackItem0 = FlowStackItem()
        val flowStackItem1 = FlowStackItem()
        val checkpoint = setupAvroCheckpoint(stackItems = listOf(flowStackItem0, flowStackItem1))

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.peek()).isEqualTo(flowStackItem1)
        assertThat(service.peek()).isEqualTo(flowStackItem1)
    }

    @Test
    fun `flow stack - peek returns null for empty stack`() {
        val checkpoint = setupAvroCheckpoint()

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.peek()).isNull()
    }

    @Test
    fun `flow stack - push adds to to the top of the stack`() {
        val flow = NonInitiatingFlowExample()
        val checkpoint = setupAvroCheckpoint()

        val service = createFlowCheckpoint(checkpoint).flowStack
        val flowStackItem = service.push(flow)
        assertThat(service.peek()).isEqualTo(flowStackItem)
    }

    @Test
    fun `flow stack - push creates and initializes stack item`() {
        val flow1 = InitiatingFlowExample()
        val flow2 = NonInitiatingFlowExample()
        val checkpoint = setupAvroCheckpoint()

        val service = createFlowCheckpoint(checkpoint).flowStack
        val flowStackItem1 = service.push(flow1)
        val flowStackItem2 = service.push(flow2)

        assertThat(flowStackItem1.flowName).isEqualTo(InitiatingFlowExample::class.qualifiedName)
        assertThat(flowStackItem1.isInitiatingFlow).isTrue
        assertThat(flowStackItem1.sessionIds).isEmpty()

        assertThat(flowStackItem2.flowName).isEqualTo(NonInitiatingFlowExample::class.qualifiedName)
        assertThat(flowStackItem2.isInitiatingFlow).isFalse
        assertThat(flowStackItem2.sessionIds).isEmpty()
    }

    @Test
    fun `flow stack - nearest first returns first match closest to the top`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())
        val flowStackItem2 = FlowStackItem("3", false, mutableListOf())

        val checkpoint = setupAvroCheckpoint(stackItems = listOf(flowStackItem0, flowStackItem1, flowStackItem2))

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.nearestFirst { it.isInitiatingFlow }).isEqualTo(flowStackItem1)
    }

    @Test
    fun `flow stack - nearest first returns null when no match found`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())

        val checkpoint = setupAvroCheckpoint(stackItems = listOf(flowStackItem0, flowStackItem1))

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.nearestFirst { it.flowName == "3" }).isNull()
    }

    @Test
    fun `rollback - original state restored when checkpoint rolled back`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())

        val session1 = SessionState().apply { sessionId = "sid1" }
        val session2 = SessionState().apply { sessionId = "sid2" }
        val fiber = ByteBuffer.wrap("abc".toByteArray())
        val checkpoint = setupAvroCheckpoint(
            suspendedOn = "s1",
            waitingFor = WaitingFor(Wakeup()),
            stackItems = listOf(flowStackItem0, flowStackItem1),
            sessionStates = listOf(session1, session2),
            newFiber = fiber,
            suspendCount = 2
        )

        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        flowCheckpoint.flowStack.pop()
        flowCheckpoint.flowStack.pop()

        flowCheckpoint.putSessionState(SessionState().apply { sessionId = "sid3" })

        flowCheckpoint.suspendedOn = "s2"
        flowCheckpoint.waitingFor = null
        val newFiber = ByteBuffer.wrap("123".toByteArray())
        flowCheckpoint.serializedFiber = newFiber

        flowCheckpoint.rollback()

        val afterRollback = flowCheckpoint.toAvro()
        assertThat(afterRollback?.flowState?.suspendedOn).isEqualTo("s1")
        assertThat(afterRollback?.flowState?.waitingFor).isEqualTo(WaitingFor(Wakeup()))
        assertThat(afterRollback?.flowState?.flowStackItems).hasSize(2)
        assertThat(afterRollback?.flowState?.sessions).hasSize(2)
        assertThat(afterRollback?.flowState?.suspendCount).isEqualTo(2)
    }

    @Test
    fun `rollback - original state restored when checkpoint rolled back from init`() {
        val flowCheckpoint = createFlowCheckpoint(setupAvroCheckpoint(initialiseFlowState = false))
        val context = FlowStartContext().apply {
            statusKey = FlowKey(FLOW_ID_1, BOB_X500_HOLDING_IDENTITY)
            identity = BOB_X500_HOLDING_IDENTITY
        }

        flowCheckpoint.initFlowState(context)
        flowCheckpoint.putSessionState(SessionState().apply { sessionId = "sid1" })
        flowCheckpoint.suspendedOn = "s2"
        flowCheckpoint.waitingFor = WaitingFor(Wakeup())

        flowCheckpoint.rollback()

        val afterRollback = flowCheckpoint.toAvro()
        assertThat(afterRollback?.flowState?.suspendedOn).isNull()
        assertThat(afterRollback?.flowState?.waitingFor).isNull()
        assertThat(afterRollback?.flowState?.sessions).isNull()
    }

    @Test
    fun `retry - mark for retry should create retry state`() {
        val flowEvent = FlowEvent()
        val error = Exception()
        val checkpoint = setupAvroCheckpoint()

        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.markForRetry(flowEvent, error)

        val result = flowCheckpoint.toAvro()!!

        assertThat(result.pipelineState.retryState).isNotNull
        assertThat(result.pipelineState.retryState.retryCount).isEqualTo(1)
        assertThat(result.pipelineState.retryState.failedEvent).isSameAs(flowEvent)
        assertThat(result.pipelineState.retryState.firstFailureTimestamp).isEqualTo(now)
        assertThat(result.pipelineState.retryState.lastFailureTimestamp).isEqualTo(now)
    }

    @Test
    fun `retry - mark for retry should apply doubling retry delay`() {
        val checkpoint1 = setupAvroCheckpoint()

        val checkpoint2 = setupAvroCheckpoint(retryState = RetryState().apply {
            retryCount = 1
        })

        val checkpoint3 = setupAvroCheckpoint(retryState = RetryState().apply {
            retryCount = 2
        })

        var flowCheckpoint = createFlowCheckpoint(checkpoint1)
        flowCheckpoint.markForRetry(FlowEvent(), Exception())
        var result = flowCheckpoint.toAvro()!!
        assertThat(result.pipelineState.maxFlowSleepDuration).isEqualTo(1000)
        assertThat(result.pipelineState.retryState.retryCount).isEqualTo(1)

        flowCheckpoint = createFlowCheckpoint(checkpoint2)
        flowCheckpoint.markForRetry(FlowEvent(), Exception())
        result = flowCheckpoint.toAvro()!!
        assertThat(result.pipelineState.maxFlowSleepDuration).isEqualTo(2000)
        assertThat(result.pipelineState.retryState.retryCount).isEqualTo(2)

        flowCheckpoint = createFlowCheckpoint(checkpoint3)
        flowCheckpoint.markForRetry(FlowEvent(), Exception())
        result = flowCheckpoint.toAvro()!!
        assertThat(result.pipelineState.maxFlowSleepDuration).isEqualTo(4000)
        assertThat(result.pipelineState.retryState.retryCount).isEqualTo(3)
    }

    @Test
    fun `retry - mark for retry should limit max retry time`() {
        val flowConfig = ConfigFactory.empty()
            .withValue(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(60000L))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_DELAY, ConfigValueFactory.fromAnyRef(3000L))
        val smartFlowConfig = SmartConfigFactory.create(flowConfig).create(flowConfig)

        val checkpoint = setupAvroCheckpoint(retryState = RetryState().apply {
            retryCount = 5
        })

        val flowCheckpoint = createFlowCheckpoint(checkpoint, smartFlowConfig)
        flowCheckpoint.markForRetry(FlowEvent(), Exception())
        val result = flowCheckpoint.toAvro()!!
        assertThat(result.pipelineState.maxFlowSleepDuration).isEqualTo(3000)
    }

    @Test
    fun `retry - creating a checkpoint with a retry state set should allow retry information to be retrieved`() {
        val flowEvent = FlowEvent("F1", Wakeup())
        val checkpoint = setupAvroCheckpoint(retryState = RetryState().apply {
            retryCount = 1
            failedEvent = flowEvent
        })

        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        assertThat(flowCheckpoint.inRetryState).isTrue
        assertThat(flowCheckpoint.retryEvent).isEqualTo(flowEvent)
        assertThat(flowCheckpoint.currentRetryCount).isEqualTo(1)
    }

    @Test
    fun `retry - marking retry as cleared should remove retry state`() {
        val flowEvent = FlowEvent("F1", Wakeup())
        val checkpoint = setupAvroCheckpoint(retryState = RetryState().apply {
            retryCount = 1
            failedEvent = flowEvent
        })

        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        flowCheckpoint.markRetrySuccess()
        assertThat(flowCheckpoint.inRetryState).isFalse
        val avroCheckpoint = flowCheckpoint.toAvro()
        assertThat(avroCheckpoint!!.pipelineState.retryState).isNull()
    }

    @Test
    fun `set sleep duration should always keep the min value seen`() {
        val checkpoint = setupAvroCheckpoint()

        val flowCheckpoint = createFlowCheckpoint(checkpoint, smartFlowConfig)

        // Defaults to configured value
        assertThat(flowCheckpoint.toAvro()!!.pipelineState.maxFlowSleepDuration).isEqualTo(60000)

        flowCheckpoint.setFlowSleepDuration(500)
        assertThat(flowCheckpoint.toAvro()!!.pipelineState.maxFlowSleepDuration).isEqualTo(500)

        flowCheckpoint.setFlowSleepDuration(1000)
        assertThat(flowCheckpoint.toAvro()!!.pipelineState.maxFlowSleepDuration).isEqualTo(500)

        flowCheckpoint.setFlowSleepDuration(200)
        assertThat(flowCheckpoint.toAvro()!!.pipelineState.maxFlowSleepDuration).isEqualTo(200)
    }

    @Test
    fun `set sleep duration should default to configured value for existing checkpoint`() {
        val checkpoint = setupAvroCheckpoint(maxFlowSleepDuration = 100)

        val flowCheckpoint = createFlowCheckpoint(checkpoint, smartFlowConfig)

        // Defaults to configured value
        assertThat(flowCheckpoint.toAvro()!!.pipelineState.maxFlowSleepDuration).isEqualTo(60000)
    }

    @Test
    fun `pending error is null by default`() {
        val (_, flowCheckpoint) = getMinimumCheckpoint()
        assertThat(flowCheckpoint.pendingPlatformError).isNull()
    }

    @Test
    fun `pending error set from checkpoint`() {
        val (checkpoint, flowCheckpoint) = getMinimumCheckpoint()
        checkpoint.pipelineState.pendingPlatformError = ExceptionEnvelope("a", "b")

        assertThat(flowCheckpoint.pendingPlatformError!!.errorType).isEqualTo("a")
        assertThat(flowCheckpoint.pendingPlatformError!!.errorMessage).isEqualTo("b")
    }

    @Test
    fun `clear pending error`() {
        val (checkpoint, flowCheckpoint) = getMinimumCheckpoint()
        checkpoint.pipelineState.pendingPlatformError = ExceptionEnvelope("a", "b")

        flowCheckpoint.clearPendingPlatformError()
        assertThat(flowCheckpoint.pendingPlatformError).isNull()
    }

    @Test
    fun `checkpoint can be read after markDeleted called`() {
        val fiber = ByteBuffer.wrap(byteArrayOf(1))
        val flow = NonInitiatingFlowExample()
        val session1 = SessionState().apply { sessionId = "S1" }
        val waitingFor = WaitingFor(Any())
        val checkpoint = setupAvroCheckpoint()
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.suspendedOn = "A"
        flowCheckpoint.waitingFor = waitingFor
        val flowStackItem = flowCheckpoint.flowStack.push(flow)
        flowCheckpoint.putSessionState(session1)
        flowCheckpoint.serializedFiber = fiber

        flowCheckpoint.markDeleted()

        assertThat(flowCheckpoint.suspendedOn).isEqualTo("A")
        assertThat(flowCheckpoint.waitingFor).isEqualTo(waitingFor)
        assertThat(flowCheckpoint.flowStack.pop()).isEqualTo(flowStackItem)
        assertThat(flowCheckpoint.sessions.first()).isEqualTo(session1)
        assertThat(flowCheckpoint.serializedFiber).isEqualTo(fiber)
    }

    @Test
    fun `checkpoint cannot be modified after markDeleted called`() {
        val checkpoint = setupAvroCheckpoint()
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.markDeleted()

        assertThrows<IllegalStateException> { flowCheckpoint.putSessionState(SessionState()) }
        assertThrows<IllegalStateException> { flowCheckpoint.markForRetry(FlowEvent(), RuntimeException()) }
        assertThrows<IllegalStateException> { flowCheckpoint.markRetrySuccess() }
        assertThrows<IllegalStateException> { flowCheckpoint.setFlowSleepDuration(1) }
    }

    @Test
    fun `existing checkpoint - can retrieve persistence state`() {
        val persistenceState = PersistenceState().apply {
            requestId = "foo"
        }
        val checkpoint = setupAvroCheckpoint(persistenceState = persistenceState)
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThat(flowCheckpoint.persistenceState).isEqualTo(persistenceState)
    }

    @Test
    fun `existing checkpoint - can set persistence state`() {
        val persistenceState = PersistenceState().apply {
            requestId = "foo"
        }
        val checkpoint = setupAvroCheckpoint()
        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        flowCheckpoint.persistenceState = persistenceState

        assertThat(flowCheckpoint.persistenceState).isEqualTo(persistenceState)
        val avroCheckpoint = flowCheckpoint.toAvro()
        assertThat(avroCheckpoint!!.flowState!!.persistenceState).isEqualTo(persistenceState)
    }
}

@InitiatingFlow("valid-example")
class InitiatingFlowExample : SubFlow<Unit> {
    override fun call() {
    }
}

class NonInitiatingFlowExample : SubFlow<Unit> {
    override fun call() {
    }
}
