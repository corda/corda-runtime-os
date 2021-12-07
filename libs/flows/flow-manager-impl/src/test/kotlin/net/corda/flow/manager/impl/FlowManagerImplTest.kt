package net.corda.flow.manager.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.output.RPCFlowResult
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.identity.HoldingIdentity
import net.corda.dependency.injection.DependencyInjectionService
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.messaging.api.records.Record
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class FlowManagerImplTest {

    class TestFlow(val jsonArg: String) : Flow<Unit> {
        override fun call() {
        }
    }

    @Test
    fun `start an initiating flow`() {

        val sandboxGroup: SandboxGroup = mock()
        val checkpointSerializerBuilder: CheckpointSerializerBuilder = mock()
        val checkpointSerializer: CheckpointSerializer = mock()
        val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory = mock()
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
        val cpiId = "cpidId"
        val checkpoint = Checkpoint(flowKey, ByteBuffer.allocate(1), cpiId, stateMachineState, emptyList())
        val eventsOut = listOf(FlowEvent(flowKey, rpcFlowResult))
        val serialized = "Test".toByteArray()
        val topic = "Topic1"

        doReturn(TestFlow::class.java).`when`(sandboxGroup).loadClassFromMainBundles(any(), eq(Flow::class.java))
        doReturn(stateMachine).`when`(flowStateMachineFactory).createStateMachine(any(), any(), any(), any(), any(), any())
        doReturn(Pair(checkpoint, eventsOut)).`when`(stateMachine).waitForCheckpoint()
        doReturn(checkpointSerializerBuilder).`when`(checkpointSerializerBuilderFactory).createCheckpointSerializerBuilder(any())
        doReturn(checkpointSerializer).`when`(checkpointSerializerBuilder).build()
        doReturn(serialized).`when`(checkpointSerializer).serialize(any())

        val flowManager = FlowManagerImpl(
            checkpointSerializerBuilderFactory,
            dependencyInjector,
            flowStateMachineFactory
        )

        val result = flowManager.startInitiatingFlow(
            FlowMetaData(flowName, flowKey, "jsonArg", cpiId, topic),
            "clientId",
            sandboxGroup
        )

        val resultRecord: Record<FlowKey, FlowEvent> = uncheckedCast(result.events.first())

        assertThat(result.checkpoint).isEqualTo(checkpoint)
        assertThat(result.events.size).isEqualTo(1)
        assertThat(resultRecord.key).isEqualTo(flowKey)
        assertThat(resultRecord.topic).isEqualTo(topic)
        assertThat(resultRecord.value?.flowKey).isEqualTo(flowKey)
        assertThat(resultRecord.value?.payload).isEqualTo(rpcFlowResult)
    }
}
