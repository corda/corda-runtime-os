package net.corda.flow.state

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.state.impl.FlowCheckpointImpl
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class FlowCheckpointImplTest {

    private fun createFlowCheckpoint(checkpoint: Checkpoint? = null): FlowCheckpointImpl {
        return FlowCheckpointImpl(checkpoint)
    }

    @Test
    fun `accessing checkpoint before initialisation should throw`() {
        val flowCheckpoint = createFlowCheckpoint()

        assertThrows<IllegalStateException> { flowCheckpoint.flowId }
        assertThrows<IllegalStateException> { flowCheckpoint.waitingFor }
        assertThrows<IllegalStateException> { flowCheckpoint.flowStack }
        assertThrows<IllegalStateException> { flowCheckpoint.serializedFiber }
        assertThrows<IllegalStateException> { flowCheckpoint.getSessionState("id") }
        assertThrows<IllegalStateException> { flowCheckpoint.putSessionState(SessionState()) }
    }

    @Test
    fun `existing checkpoint - ensures flow stack items are initialised`() {
        val flowStackItem = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            flowStackItems = listOf(flowStackItem)
        }
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThat(flowCheckpoint.flowStack.peekFirst().sessionIds).isNotNull
    }

    @Test
    fun `existing checkpoint - guard against null flow state`() {
        val checkpoint = Checkpoint().apply {
            flowStartContext = FlowStartContext()
        }

        val error = assertThrows<IllegalStateException> { createFlowCheckpoint(checkpoint) }

        assertThat(error.message).isEqualTo("The flow state has not been set on the checkpoint.")
    }

    @Test
    fun `existing checkpoint - guard against null flow start context`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
        }

        val error = assertThrows<IllegalStateException> { createFlowCheckpoint(checkpoint) }

        assertThat(error.message).isEqualTo("The flow start context has not been set on the checkpoint.")
    }

    @Test
    fun `existing checkpoint - guard against duplicate sessions`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val session2 = SessionState().apply { sessionId = "S1" }
        val checkpoint = Checkpoint().apply {
            flowId = "F1"
            sessions = listOf(session1, session2)
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        val error = assertThrows<IllegalStateException> { createFlowCheckpoint(checkpoint) }

        assertThat(error.message).isEqualTo("Invalid checkpoint, flow 'F1' has duplicate session for Session ID = 'S1'")
    }

    @Test
    fun `existing checkpoint - sets sessions`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val session2 = SessionState().apply { sessionId = "S2" }
        val checkpoint = Checkpoint().apply {
            sessions = listOf(session1, session2)
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        assertThat(flowCheckpoint.sessions).containsOnly(session1, session2)
    }

    @Test
    fun `existing checkpoint - sets flow id`() {
        val checkpoint = Checkpoint().apply {
            flowId = "F1"
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        assertThat(createFlowCheckpoint(checkpoint).flowId).isEqualTo("F1")
    }

    @Test
    fun `existing checkpoint - sets flow key`() {
        val flowKey = FlowKey("R1", BOB_X500_HOLDING_IDENTITY)
        val checkpoint = Checkpoint().apply {
            flowStartContext = FlowStartContext().apply {
                statusKey = flowKey
                flowState = StateMachineState()
                flowStartContext = FlowStartContext()
            }
        }

        assertThat(createFlowCheckpoint(checkpoint).flowKey).isEqualTo(flowKey)
    }

    @Test
    fun `existing checkpoint - sets holding identity`() {
        val flowKey = FlowKey("R1", BOB_X500_HOLDING_IDENTITY)
        val checkpoint = Checkpoint().apply {
            flowStartContext = FlowStartContext().apply {
                statusKey = flowKey
                identity = BOB_X500_HOLDING_IDENTITY
                flowState = StateMachineState()
                flowStartContext = FlowStartContext()
            }
        }

        assertThat(createFlowCheckpoint(checkpoint).holdingIdentity).isEqualTo(BOB_X500_HOLDING_IDENTITY)
    }

    @Test
    fun `existing checkpoint - sets flow start context`() {
        val context = FlowStartContext()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = context
        }

        assertThat(createFlowCheckpoint(checkpoint).flowStartContext).isEqualTo(context)
    }

    @Test
    fun `existing checkpoint - sets suspended on`() {
        val checkpoint = Checkpoint().apply {
            flowStartContext = FlowStartContext()
            flowState = StateMachineState().apply {
                suspendedOn = "classA"
            }
        }

        assertThat(createFlowCheckpoint(checkpoint).suspendedOn).isEqualTo("classA")
    }

    @Test
    fun `existing checkpoint - sets waiting for`() {
        val waitingFor = WaitingFor(Any())
        val checkpoint = Checkpoint().apply {
            flowStartContext = FlowStartContext()
            flowState = StateMachineState().apply {
                this.waitingFor = waitingFor
            }
        }

        assertThat(createFlowCheckpoint(checkpoint).waitingFor).isEqualTo(waitingFor)
    }

    @Test
    fun `existing checkpoint - sets serialised fiber`() {
        val fiber = ByteBuffer.wrap(byteArrayOf())
        val checkpoint = Checkpoint().apply {
            this.fiber = fiber
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        assertThat(createFlowCheckpoint(checkpoint).serializedFiber).isEqualTo(fiber)
    }

    @Test
    fun `get session`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val session2 = SessionState().apply { sessionId = "S2" }
        val checkpoint = Checkpoint().apply {
            sessions = listOf(session1, session2)
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        assertThat(createFlowCheckpoint(checkpoint).getSessionState("S2")).isEqualTo(session2)
    }

    @Test
    fun `put session`() {
        val session1 = SessionState().apply { sessionId = "S1" }
        val checkpoint = Checkpoint().apply {
            sessions = listOf()
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        val flowCheckpoint = createFlowCheckpoint(checkpoint)
        flowCheckpoint.putSessionState(session1)
        assertThat(flowCheckpoint.sessions).containsOnly(session1)
    }

    @Test
    fun `init checkpoint`() {
        val flowId = "F1"
        val waitingFor = WaitingFor(Any())
        val flowKey = FlowKey("R1", BOB_X500_HOLDING_IDENTITY)
        val flowStartContext = FlowStartContext().apply {
            statusKey = flowKey
            identity = BOB_X500_HOLDING_IDENTITY
        }

        val flowCheckpoint = createFlowCheckpoint()

        flowCheckpoint.initFromNew(flowId, flowStartContext, waitingFor)

        assertThat(flowCheckpoint.flowId).isEqualTo(flowId)
        assertThat(flowCheckpoint.flowKey).isEqualTo(flowKey)
        assertThat(flowCheckpoint.flowStartContext).isEqualTo(flowStartContext)
        assertThat(flowCheckpoint.holdingIdentity).isEqualTo(BOB_X500_HOLDING_IDENTITY)
        assertThat(flowCheckpoint.suspendedOn).isNull()
        assertThat(flowCheckpoint.waitingFor).isEqualTo(waitingFor)
        assertThat(flowCheckpoint.flowStack.size).isEqualTo(0)
        assertThat(flowCheckpoint.sessions.size).isEqualTo(0)
    }

    @Test
    fun `to avro returns updated checkpoint`() {
        val fiber = ByteBuffer.wrap(byteArrayOf(1))
        val flow = NonInitiatingFlowExample()
        val session1 = SessionState().apply { sessionId = "S1" }
        val waitingFor = WaitingFor(Any())
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }
        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.suspendedOn = "A"
        flowCheckpoint.waitingFor = waitingFor
        val flowStackItem = flowCheckpoint.flowStack.push(flow)
        flowCheckpoint.putSessionState(session1)
        flowCheckpoint.serializedFiber = fiber

        val avroCheckpoint = flowCheckpoint.toAvro()!!

        assertThat(avroCheckpoint.flowState.suspendedOn).isEqualTo("A")
        assertThat(avroCheckpoint.flowState.waitingFor).isEqualTo(waitingFor)
        assertThat(avroCheckpoint.flowStackItems.first()).isEqualTo(flowStackItem)
        assertThat(avroCheckpoint.sessions.first()).isEqualTo(session1)
        assertThat(avroCheckpoint.fiber).isEqualTo(fiber)

        flowCheckpoint.markDeleted()
        assertThat(flowCheckpoint.toAvro()).isNull()
    }

    @Test
    fun `mark delete returns null from to avro`() {
        val checkpoint = Checkpoint().apply {
            flowId = "F1"
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
        }

        val flowCheckpoint = createFlowCheckpoint(checkpoint)

        flowCheckpoint.markDeleted()
        assertThat(flowCheckpoint.toAvro()).isNull()
    }

    @Test
    fun `flow stack - peek first throws if stack empty`() {
        val flowCheckpoint = createFlowCheckpoint()
        assertThrows<IllegalStateException> { flowCheckpoint.flowStack.peekFirst() }
    }

    @Test
    fun `flow stack - peek first returns first item on the stack`() {
        val item1 = FlowStackItem()
        val item2 = FlowStackItem()

        val flowCheckpoint = createFlowCheckpoint(Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            flowStackItems = listOf(item1, item2)
        })

        assertThat(flowCheckpoint.flowStack.peekFirst()).isSameAs(item1)
    }

    @Test
    fun `flow stack - pop removes and returns top item`() {

        val flowStackItem0 = FlowStackItem()
        val flowStackItem1 = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1)
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.pop()).isEqualTo(flowStackItem1)
        assertThat(service.pop()).isEqualTo(flowStackItem0)

    }

    @Test
    fun `flow stack - pop removes and returns null when stack empty`() {
        val flowStackItem0 = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf(flowStackItem0)
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.pop()).isEqualTo(flowStackItem0)
        assertThat(service.pop()).isNull()
    }

    @Test
    fun `flow stack - peek returns top item`() {
        val flowStackItem0 = FlowStackItem()
        val flowStackItem1 = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1)
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.peek()).isEqualTo(flowStackItem1)
        assertThat(service.peek()).isEqualTo(flowStackItem1)
    }

    @Test
    fun `flow stack - peek returns null for empty stack`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf()
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.peek()).isNull()
    }

    @Test
    fun `flow stack - push adds to to the top of the stack`() {
        val flow = NonInitiatingFlowExample()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf()
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        val flowStackItem = service.push(flow)
        assertThat(service.peek()).isEqualTo(flowStackItem)
    }

    @Test
    fun `flow stack - push creates and initializes stack item`() {
        val flow1 = InitiatingFlowExample()
        val flow2 = NonInitiatingFlowExample()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf()
        }

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
    fun `flow stack - initiating flow with version less than 1 is invalid`() {
        val flow1 = InvalidInitiatingFlowExample()
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf()
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        val error = assertThrows<IllegalArgumentException> { service.push(flow1) }
        assertThat(error.message).isEqualTo("Flow versions have to be greater or equal to 1")
    }

    @Test
    fun `flow stack - nearest first returns first match closest to the top`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())
        val flowStackItem2 = FlowStackItem("3", false, mutableListOf())

        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1, flowStackItem2)
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.nearestFirst { it.isInitiatingFlow }).isEqualTo(flowStackItem1)
    }

    @Test
    fun `flow stack - nearest first returns null when no match found`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())

        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowStartContext = FlowStartContext()
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1)
        }

        val service = createFlowCheckpoint(checkpoint).flowStack
        assertThat(service.nearestFirst { it.flowName == "3" }).isNull()
    }
}

@Suppress("Unused")
@InitiatingFlow(1)
class InitiatingFlowExample : Flow<Unit> {
    override fun call() {
    }
}

@Suppress("Unused")
@InitiatingFlow(0)
class InvalidInitiatingFlowExample : Flow<Unit> {
    override fun call() {
    }
}

@Suppress("Unused")
class NonInitiatingFlowExample : Flow<Unit> {
    override fun call() {
    }
}
    