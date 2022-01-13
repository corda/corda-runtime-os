package net.corda.dependency.injection.impl

import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DependencyInjectionBuilderImplTest {

    @Test
    fun `create returns configured injector`(){
        val sandboxGroup: SandboxGroup = mock()
        val sandboxGroupContext: MutableSandboxGroupContext = mock()
        doReturn(sandboxGroup).whenever(sandboxGroupContext).sandboxGroup

        val dependencyInjectionBuilder = DependencyInjectionBuilderImpl(listOf(), listOf())
        dependencyInjectionBuilder.addSandboxDependencies(sandboxGroupContext)

        assertThat(dependencyInjectionBuilder.build()).isInstanceOf(FlowDependencyInjector::class.java)
    }

    @Test
    fun `create throws exception if called without setting a sandbox`(){
        val dependencyInjectionBuilder = DependencyInjectionBuilderImpl(listOf(), listOf())

         assertThatIllegalStateException().isThrownBy { dependencyInjectionBuilder.build()}
    }
}
