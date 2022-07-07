package net.corda.flow.fiber

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.services.MockFlowFiberService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class FlowFiberExecutionContextTest {

    @Test
    fun `test member x500 name is taken from holding identity`() {
        val target = MockFlowFiberService().flowFiberExecutionContext
        assertThat(target.memberX500Name).isEqualTo(BOB_X500_NAME)
    }

    @Test
    fun `getMemberX500Name returns x500name parsed from holding identity`() {
        val holdingIdentity = HoldingIdentity(BOB_X500_NAME.toString(), "group1")
        val context = FlowFiberExecutionContext(mock(), mock(), holdingIdentity, mock())
        assertThat(context.memberX500Name).isEqualTo(BOB_X500_NAME)
    }

    @Test
    fun `getMemberX500Name throws when the x500 name is invalid`() {
        val holdingIdentity = HoldingIdentity("x500", "group1")
        val exception = assertThrows<IllegalStateException> {
            FlowFiberExecutionContext(mock(), mock(), holdingIdentity, mock())
        }
        assertThat(exception.message).isEqualTo("Failed to convert Holding Identity x500 name 'x500' to MemberX500Name")
    }
}