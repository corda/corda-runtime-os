package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.Wakeup
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.pipeline.factory.impl.FlowFiberExecutionContextFactoryImpl
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowFiberExecutionContextFactoryImplTest {

    private val flowSandboxService = mock<FlowSandboxService>()
    private val sandboxGroupContext = mock<SandboxGroupContext>()
    private val checkpointSerializer = mock<CheckpointSerializer>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider>()
    private val membershipGroupReader = mock<MembershipGroupReader>()
    private val flowFiberExecutionContextFactory = FlowFiberExecutionContextFactoryImpl(
        flowSandboxService,
        membershipGroupReaderProvider
    )

    @Test
    fun `create fiber execution context returns initialized context instance`() {
        val flowStartContext = FlowStartContext().apply {
            initiatedBy = BOB_X500_HOLDING_IDENTITY
        }

        val context = buildFlowEventContext<Any>(Wakeup())

        whenever(context.checkpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(context.checkpoint.holdingIdentity).thenReturn(BOB_X500_HOLDING_IDENTITY)
        whenever(flowSandboxService.get(BOB_X500_HOLDING_IDENTITY.toCorda())).thenReturn(sandboxGroupContext)
        whenever(membershipGroupReaderProvider.getGroupReader(
            BOB_X500_HOLDING_IDENTITY.toCorda()
        )).thenReturn(membershipGroupReader)
        whenever(sandboxGroupContext.get(
            FlowSandboxContextTypes.DEPENDENCY_INJECTOR,
            SandboxDependencyInjector::class.java)
        ).thenReturn(sandboxDependencyInjector)

        whenever(sandboxGroupContext.get(
            FlowSandboxContextTypes.CHECKPOINT_SERIALIZER,
            CheckpointSerializer::class.java)
        ).thenReturn(checkpointSerializer)

        val result = flowFiberExecutionContextFactory.createFiberExecutionContext(context)

        assertThat(result.flowCheckpoint).isSameAs(context.checkpoint)
        assertThat(result.holdingIdentity).isEqualTo(BOB_X500_HOLDING_IDENTITY)
        assertThat(result.sandboxGroupContext).isSameAs(sandboxGroupContext)
        assertThat(result.sandboxDependencyInjector).isSameAs(sandboxDependencyInjector)
        assertThat(result.checkpointSerializer).isSameAs(checkpointSerializer)
        assertThat(result.checkpointSerializer).isSameAs(checkpointSerializer)
        assertThat(result.membershipGroupReader).isSameAs(membershipGroupReader)
        assertThat(result.memberX500Name).isEqualTo(BOB_X500_NAME)

    }
}