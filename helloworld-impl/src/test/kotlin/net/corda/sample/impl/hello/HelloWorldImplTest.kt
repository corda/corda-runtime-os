package net.corda.sample.impl.hello

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HelloWorldImplTest {
    @Test
    fun `prints HelloWorld`() {
        assertThat(HelloWorldImpl().sayHello()).isEqualTo("Hello World!")
    }
}