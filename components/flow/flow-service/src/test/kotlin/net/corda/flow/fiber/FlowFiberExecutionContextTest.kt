package net.corda.flow.fiber

import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.services.MockFlowFiberService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowFiberExecutionContextTest {

    @Test
    fun `test member x500 name is taken from holding identity`() {
        val target = MockFlowFiberService().flowFiberExecutionContext
        assertThat(target.memberX500Name).isEqualTo(BOB_X500_NAME)
    }
}