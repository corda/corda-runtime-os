package net.corda.flow.service

import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.flow.manager.factory.SandboxDependencyInjectionFactory
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.service.SandboxService
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.serialization.checkpoint.CheckpointSerializerBuilder
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSandboxServiceImplTest {

    private val dependencyInjectionFactory: SandboxDependencyInjectionFactory = mock()
    private val sandboxDependencyInjector: SandboxDependencyInjector = mock()
    private val singletonRegistrations: Set<SingletonSerializeAsToken> = setOf(mock())

    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory = mock()
    private val checkpointSerializerBuilder: CheckpointSerializerBuilder = mock()
    private val checkpointSerializer: CheckpointSerializer = mock()

    private val sandboxService: SandboxService = mock()

    private val mutableSandboxGroupContext: MutableSandboxGroupContext = mock()
    private val sandboxGroup: SandboxGroup = mock()

    private val holderIdentity = HoldingIdentity("", "")
    private val cpi: CPI.Identifier = mock()

    private val flowSandboxService = FlowSandboxServiceImpl(
        sandboxService,
        dependencyInjectionFactory,
        checkpointSerializerBuilderFactory
    )
    private val initCallbackCaptor = argumentCaptor<SandboxGroupContextInitializer>()
    private val addSingletonsCaptor = argumentCaptor<Set<SingletonSerializeAsToken>>()

    @BeforeEach
    fun setup() {
        doReturn(mutableSandboxGroupContext).whenever(sandboxService)
            .getOrCreate(any(), initCallbackCaptor.capture())
        doReturn(mutableSandboxGroupContext).whenever(sandboxService)
            .getOrCreateByCpiIdentifier(any(), any(), any(), initCallbackCaptor.capture())
        doReturn(sandboxGroup).whenever(mutableSandboxGroupContext).sandboxGroup

        doReturn(sandboxDependencyInjector).whenever(dependencyInjectionFactory).create(mutableSandboxGroupContext)
        doReturn(singletonRegistrations).whenever(sandboxDependencyInjector).getRegisteredSingletons()

        doReturn(checkpointSerializerBuilder)
            .whenever(checkpointSerializerBuilderFactory).createCheckpointSerializerBuilder(any())
        doReturn(checkpointSerializerBuilder)
            .whenever(checkpointSerializerBuilder).addSingletonSerializableInstances(addSingletonsCaptor.capture())
        doReturn(checkpointSerializer).whenever(checkpointSerializerBuilder).build()
    }

    @Test
    fun `get should return group context provided by the sandbox service`() {
        val sandbox = flowSandboxService.get(holderIdentity, cpi)
        assertThat(sandbox).isSameAs(mutableSandboxGroupContext)
    }

    @Test
    fun `get should initialise injector and add to group context`() {
        flowSandboxService.get(holderIdentity, cpi)

        // invoke the callback
        initCallbackCaptor.firstValue.initializeSandboxGroupContext(holderIdentity, mutableSandboxGroupContext)

        verify(dependencyInjectionFactory, times(1)).create(mutableSandboxGroupContext)

        verify(mutableSandboxGroupContext, times(1))
            .putObjectByKey(FlowSandboxContextTypes.DEPENDENCY_INJECTOR, sandboxDependencyInjector)
    }

    @Test
    fun `get should initialise serializer and add to group context`() {
        flowSandboxService.get(holderIdentity, cpi)

        // invoke the callback
        initCallbackCaptor.firstValue.initializeSandboxGroupContext(holderIdentity, mutableSandboxGroupContext)

        verify(checkpointSerializerBuilder, times(1)).build()

        // Expect singletons injector + the sandbox group to be added to the serializer
        val allSingletons = addSingletonsCaptor.allValues.flatten()
        assertThat(allSingletons).containsAll(listOf(sandboxGroup, singletonRegistrations.first()))

        verify(mutableSandboxGroupContext, times(1))
            .putObjectByKey(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER, checkpointSerializer)
    }
}
