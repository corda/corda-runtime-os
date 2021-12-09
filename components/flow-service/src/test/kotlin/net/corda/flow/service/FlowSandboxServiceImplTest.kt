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
import net.corda.virtualnode.sandboxgroup.MutableSandboxGroupContext
import net.corda.virtualnode.sandboxgroup.SandboxGroupService
import net.corda.virtualnode.HoldingIdentity
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
    private val singletonRegistrations: Set<SingletonSerializeAsToken> = setOf(mock())

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
    private val initCallbackCaptor = argumentCaptor<(HoldingIdentity, MutableSandboxGroupContext) -> AutoCloseable>()
    private val addSingletonsCaptor = argumentCaptor<Set<SingletonSerializeAsToken>>()

    @BeforeEach
    fun setup() {
        doReturn(sandboxGroupContext).whenever(sandboxGroupService)
            .get(any(),  initCallbackCaptor.capture())
        doReturn(sandboxGroup).whenever(sandboxGroupContext).sandboxGroup

        doReturn(dependencyInjectionBuilder).whenever(dependencyInjectionBuilderFactory).create()
        doReturn(flowDependencyInjector).whenever(dependencyInjectionBuilder).build()
        doReturn(singletonRegistrations).whenever(flowDependencyInjector).getRegisteredAsTokenSingletons()

        doReturn(checkpointSerializerBuilder)
            .whenever(checkpointSerializerBuilderFactory).createCheckpointSerializerBuilder(any())
        doReturn(checkpointSerializerBuilder)
            .whenever(checkpointSerializerBuilder).addSingletonSerializableInstances(addSingletonsCaptor.capture())
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
        initCallbackCaptor.firstValue(holderIdentity, sandboxGroupContext)

        val inOrder = inOrder(dependencyInjectionBuilder)
        inOrder.verify(dependencyInjectionBuilder, times(1)).addSandboxDependencies(sandboxGroupContext)
        inOrder.verify(dependencyInjectionBuilder, times(1)).build()

        verify(sandboxGroupContext, times(1))
            .put(FlowSandboxContextTypes.DEPENDENCY_INJECTOR, flowDependencyInjector)
    }

    @Test
    fun `get should initialise serializer and add to group context`() {
        flowSandboxService.get(holderIdentity, cpi)

        // invoke the callback
        initCallbackCaptor.firstValue(holderIdentity, sandboxGroupContext)

        verify(checkpointSerializerBuilder, times(1)).build()

        // Expect singletons injector + the sandbox group to be added to the serializer
        val allSingletons = addSingletonsCaptor.allValues.flatten()
        assertThat(allSingletons).containsAll(listOf(sandboxGroup, singletonRegistrations.first()))

        verify(sandboxGroupContext, times(1))
            .put(FlowSandboxContextTypes.DEPENDENCY_INJECTOR, flowDependencyInjector)
    }
}