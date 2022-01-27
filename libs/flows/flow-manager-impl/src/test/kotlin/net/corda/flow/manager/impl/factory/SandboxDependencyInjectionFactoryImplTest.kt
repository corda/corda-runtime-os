package net.corda.flow.manager.impl.factory

import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SandboxDependencyInjectionFactoryImplTest {

    @Test
    fun `create returns instance of injector`(){
        val singleton = mock<SingletonSerializeAsToken>()
        val singletons = listOf(singleton)
        val factory  = SandboxDependencyInjectionFactoryImpl(singletons)
        val injector = factory.create(mock())
        assertThat(injector).isInstanceOf(SandboxDependencyInjector::class.java)
        assertThat(injector.getRegisteredSingletons()).containsExactly(singleton)
    }
}