package net.corda.flow.fiber

import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FlowFiberExecutionContextTest {

    @Test
    fun `test member x500 name is taken from holding identity`() {
        val target = MockFlowFiberService().flowFiberExecutionContext
        assertThat(target.memberX500Name).isEqualTo(BOB_X500_NAME)
    }

    @Test
    fun `getMemberX500Name returns x500name parsed from holding identity`() {
        val holdingIdentity = HoldingIdentity(BOB_X500_NAME, "group1")
        val context = FlowFiberExecutionContext(mock(), mock(), holdingIdentity, mock(), mock(), emptyMap())
        assertThat(context.memberX500Name).isEqualTo(BOB_X500_NAME)
    }

}