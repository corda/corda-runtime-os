package net.corda.simulator.runtime.serialization;

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.isA
import org.junit.jupiter.api.Test;

class SimpleJsonMarshallingServiceFactoryTest {

    @Test
    fun `should create a JSONMarshallingService`() {
        val factory = SimpleJsonMarshallingServiceFactory()
        assertThat(factory.create(), isA(SimpleJsonMarshallingService::class.java))
    }
}
