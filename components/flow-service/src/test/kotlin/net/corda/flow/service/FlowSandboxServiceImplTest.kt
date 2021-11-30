package net.corda.flow.service

import net.corda.dependency.injection.DependencyInjectionBuilder
import net.corda.dependency.injection.DependencyInjectionBuilderFactory
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtual.node.context.HoldingIdentity
import net.corda.virtual.node.sandboxgroup.MutableSandboxGroupContext
import net.corda.virtual.node.sandboxgroup.SandboxGroupService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSandboxServiceImplTest {

    private val dependencyInjectionBuilderFactory: DependencyInjectionBuilderFactory = mock()
    private val dependencyInjectionBuilder: DependencyInjectionBuilder = mock()
    private val flowDependencyInjector: FlowDependencyInjector = mock()
    private val singletonRegistrations: Set<SingletonSerializeAsToken> = setOf()

    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory = mock()
    private val checkpointSerializerBuilder: CheckpointSerializerBuilder = mock()
    private val checkpointSerializer: CheckpointSerializer = mock()

    private val sandboxGroupService: SandboxGroupService = mock()
    private val sandboxGroupContext: MutableSandboxGroupContext = mock()
    private val sandboxGroup: SandboxGroup = mock()

    private val holderIdentity = HoldingIdentity("", "")
    private val cpi: CPI.Identifier = mock()

    private val flowSandboxService = FlowSandboxServiceImpl(
        sandboxGroupService,
        dependencyInjectionBuilderFactory,
        checkpointSerializerBuilderFactory
    )
    private val captor = argumentCaptor<(HoldingIdentity, MutableSandboxGroupContext) -> AutoCloseable>()

    @BeforeEach
    fun setup() {
        doReturn(sandboxGroupContext).whenever(sandboxGroupService).get(any(), any(), any(),captor.capture())
        doReturn(sandboxGroup).whenever(sandboxGroupContext).sandboxGroup

        doReturn(dependencyInjectionBuilder).whenever(dependencyInjectionBuilderFactory).create()
        doReturn(flowDependencyInjector).whenever(dependencyInjectionBuilder).build()
        doReturn(singletonRegistrations).whenever(flowDependencyInjector).getRegisteredAsTokenSingletons()

        doReturn(checkpointSerializerBuilder).whenever(checkpointSerializerBuilderFactory).createCheckpointSerializerBuilder(any())
        doReturn(checkpointSerializer).whenever(checkpointSerializerBuilder).build()
    }

    @Test
    fun `get should return group context provided by the sandbox service`() {
        val sandbox = flowSandboxService.get(holderIdentity, cpi)
        assertThat(sandbox).isSameAs(sandboxGroupContext)
    }

    @Test
    fun `get should initialise injector and add to group context`() {
        flowSandboxService.get(holderIdentity, cpi)

        // invoke the callback
        captor.firstValue(holderIdentity,sandboxGroupContext)

        val inOrder = inOrder(dependencyInjectionBuilder)
        inOrder.verify(dependencyInjectionBuilder, times(1)).addSandboxDependencies(sandboxGroupContext)
        inOrder.verify(dependencyInjectionBuilder, times(1)).build()

        verify(sandboxGroupContext, times(1)).put(FlowSandboxContextTypes.DEPENDENCY_INJECTOR,flowDependencyInjector)
    }
}