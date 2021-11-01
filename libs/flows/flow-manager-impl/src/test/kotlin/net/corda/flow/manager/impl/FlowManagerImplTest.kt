package net.corda.flow.manager.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.RPCFlowResult
import net.corda.data.flow.StateMachineState
import net.corda.data.flow.event.FlowEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.dependency.injection.DependencyInjectionService
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import net.corda.virtual.node.cache.FlowMetadata
import net.corda.virtual.node.cache.VirtualNodeCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class FlowManagerImplTest {

    class TestFlow: Flow<Unit> {
        override fun call() {
        }
    }

    @Test
    fun `start an initiating flow`() {

        val sandboxGroup: SandboxGroup = mock()
        val virtualNodeCache: VirtualNodeCache = mock()
        val checkpointSerialisationService: SerializationService = mock()
        val dependencyInjector: DependencyInjectionService = mock()
        val flowStateMachineFactory: FlowStateMachineFactory = mock()
        val stateMachine: FlowStateMachine<*> = mock()

        val identity = HoldingIdentity("Alice", "group")
        val flowKey = FlowKey("some-id", identity)
        val flowName = "flow"
        val rpcFlowResult = RPCFlowResult(
            "",
            flowName,
            "Pass!",
            SecureHash("", ByteBuffer.allocate(1)),
            ExceptionEnvelope()
        )
        val stateMachineState = StateMachineState(
            "",
            1,
            false,
            ByteBuffer.allocate(1),
            emptyList()
        )
        val checkpoint = Checkpoint(flowKey, ByteBuffer.allocate(1), stateMachineState)
        val eventsOut = listOf(FlowEvent(flowKey, rpcFlowResult))
        val flowMetadata = FlowMetadata(flowName, flowKey)
        val serialized = SerializedBytes<String>("Test".toByteArray())

        doReturn(sandboxGroup).`when`(virtualNodeCache).getSandboxGroupFor(any(), any())
        doReturn(TestFlow::class.java).`when`(sandboxGroup).loadClassFromMainBundles(any(), eq(Flow::class.java))
        doReturn(stateMachine).`when`(flowStateMachineFactory).createStateMachine(any(), any(), any(), any())
        doReturn(Pair(checkpoint, eventsOut)).`when`(stateMachine).waitForCheckpoint()
        doReturn(serialized).`when`(checkpointSerialisationService).serialize(any())

        val flowManager = FlowManagerImpl(
            virtualNodeCache,
            checkpointSerialisationService,
            dependencyInjector,
            flowStateMachineFactory
        )

        val result = flowManager.startInitiatingFlow(
            flowMetadata,
            "",
            emptyList()
        )

        assertThat(result.checkpoint).isEqualTo(checkpoint)
        assertThat(result.events.size).isEqualTo(1)
        assertThat(result.events.first().key).isEqualTo(flowName)
        assertThat(result.events.first().topic).isEqualTo("")
        assertThat(result.events.first().value).isEqualTo(serialized.bytes)
    }
}
