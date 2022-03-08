package net.corda.flow.fiber.factory

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowStackService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class FlowStackServiceFactoryImplTest {

    @Test
    fun `create returns instance of the flow stack service`() {
        val factory = FlowStackServiceFactoryImpl()
        val stackItem = FlowStackItem()
        val checkpoint = Checkpoint()
        checkpoint.flowStackItems = mutableListOf(stackItem)

        val service = factory.create(checkpoint)

        Assertions.assertThat(service).isInstanceOf(FlowStackService::class.java)
        Assertions.assertThat(service.peek()).isEqualTo(stackItem)
    }
}