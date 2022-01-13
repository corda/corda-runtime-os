package net.corda.dependency.injection.impl

import net.corda.dependency.injection.DependencyInjectionBuilder

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DependencyInjectionBuilderFactoryImplTest {

    @Test
    fun `create returns instance of builder`(){
        val builder  = DependencyInjectionBuilderFactoryImpl(listOf(), listOf())
        assertThat(builder.create()).isInstanceOf(DependencyInjectionBuilder::class.java)
    }
}