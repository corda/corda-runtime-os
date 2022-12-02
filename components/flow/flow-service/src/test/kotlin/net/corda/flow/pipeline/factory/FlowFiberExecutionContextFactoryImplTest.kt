package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.Wakeup
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.pipeline.factory.impl.FlowFiberExecutionContextFactoryImpl
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.MDC

class FlowFiberExecutionContextFactoryImplTest {

    private val flowSandboxService = mock<FlowSandboxService>()
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val checkpointSerializer = mock<CheckpointSerializer>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider>()
    private val membershipGroupReader = mock<MembershipGroupReader>()
    private val flowFiberExecutionContextFactory = FlowFiberExecutionContextFactoryImpl(
        flowSandboxService,
        membershipGroupReaderProvider,
        currentSandboxGroupContext
    )

    val mdcMock = Mockito.mockStatic(MDC::class.java).also {
        it.`when`<Map<String, String>> {
            MDC.getCopyOfContextMap()
        }.thenReturn(emptyMap())
    }

    @AfterEach
    fun setup() {
        mdcMock.close()
    }

    @Test
    fun `create fiber execution context returns initialized context instance`() {
        val flowStartContext = FlowStartContext().apply {
            identity = BOB_X500_HOLDING_IDENTITY
        }

        val context = buildFlowEventContext<Any>(Wakeup())

        whenever(context.checkpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(context.checkpoint.holdingIdentity).thenReturn(BOB_X500_HOLDING_IDENTITY.toCorda())
        whenever(flowSandboxService.get(BOB_X500_HOLDING_IDENTITY.toCorda())).thenReturn(sandboxGroupContext)
        whenever(membershipGroupReaderProvider.getGroupReader(
            BOB_X500_HOLDING_IDENTITY.toCorda()
        )).thenReturn(membershipGroupReader)
        whenever(sandboxGroupContext.dependencyInjector).thenReturn(sandboxDependencyInjector)

        whenever(sandboxGroupContext.checkpointSerializer).thenReturn(checkpointSerializer)

        val result = flowFiberExecutionContextFactory.createFiberExecutionContext(context)

        assertThat(result.flowCheckpoint).isSameAs(context.checkpoint)
        assertThat(result.holdingIdentity).isEqualTo(BOB_X500_HOLDING_IDENTITY.toCorda())
        assertThat(result.sandboxGroupContext).isSameAs(sandboxGroupContext)
        assertThat(result.membershipGroupReader).isSameAs(membershipGroupReader)
        assertThat(result.memberX500Name).isEqualTo(BOB_X500_NAME)
        assertThat(result.currentSandboxGroupContext).isEqualTo(currentSandboxGroupContext)

    }
}