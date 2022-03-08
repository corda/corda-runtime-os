package net.corda.flow.fiber

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.state.Checkpoint
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowStackServiceImplTest {

    @Test
    fun `pop removes and returns top item`() {
        val flowStackItem0 = FlowStackItem()
        val flowStackItem1 = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1)
        }

        val service = FlowStackServiceImpl(checkpoint)
        assertThat(service.pop()).isEqualTo(flowStackItem1)
        assertThat(service.pop()).isEqualTo(flowStackItem0)
    }

    @Test
    fun `pop removes and returns null when stack empty`() {
        val flowStackItem0 = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf(flowStackItem0)
        }

        val service = FlowStackServiceImpl(checkpoint)
        assertThat(service.pop()).isEqualTo(flowStackItem0)
        assertThat(service.pop()).isNull()
    }

    @Test
    fun `peek returns top item`() {
        val flowStackItem0 = FlowStackItem()
        val flowStackItem1 = FlowStackItem()
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1)
        }

        val service = FlowStackServiceImpl(checkpoint)
        assertThat(service.peek()).isEqualTo(flowStackItem1)
        assertThat(service.peek()).isEqualTo(flowStackItem1)
    }

    @Test
    fun `peek returns null for empty stack`() {
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf()
        }

        val service = FlowStackServiceImpl(checkpoint)
        assertThat(service.peek()).isNull()
    }

    @Test
    fun `push adds to to the top of the stack`() {
        val flow = NonInitiatingFlowExample()
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf()
        }

        val service = FlowStackServiceImpl(checkpoint)
        val flowStackItem = service.push(flow)
        assertThat(service.peek()).isEqualTo(flowStackItem)
    }

    @Test
    fun `push creates and initializes stack item`() {
        val flow1 = InitiatingFlowExample()
        val flow2 = NonInitiatingFlowExample()
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf()
        }

        val service = FlowStackServiceImpl(checkpoint)
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
    fun `initiating flow with version less than 1 is invalid`() {
        val flow1 = InvalidInitiatingFlowExample()
        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf()
        }

        val service = FlowStackServiceImpl(checkpoint)
        val error = assertThrows<IllegalArgumentException> { service.push(flow1) }
        assertThat(error.message).isEqualTo("Flow versions have to be greater or equal to 1")
    }

    @Test
    fun `nearest first returns first match closest to the top`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())
        val flowStackItem2 = FlowStackItem("3", false, mutableListOf())

        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1, flowStackItem2)
        }

        val service = FlowStackServiceImpl(checkpoint)
        assertThat(service.nearestFirst { it.isInitiatingFlow }).isEqualTo(flowStackItem1)
    }

    @Test
    fun `nearest first returns null when no match found`() {
        val flowStackItem0 = FlowStackItem("1", false, mutableListOf())
        val flowStackItem1 = FlowStackItem("2", true, mutableListOf())

        val checkpoint = Checkpoint().apply {
            this.flowStackItems = mutableListOf(flowStackItem0, flowStackItem1)
        }

        val service = FlowStackServiceImpl(checkpoint)
        assertThat(service.nearestFirst { it.flowName == "3" }).isNull()
    }
}

@InitiatingFlow(1)
class InitiatingFlowExample : Flow<Unit> {
    override fun call() {
    }
}

@InitiatingFlow(0)
class InvalidInitiatingFlowExample : Flow<Unit> {
    override fun call() {
    }
}

class NonInitiatingFlowExample : Flow<Unit> {
    override fun call() {
    }
}
