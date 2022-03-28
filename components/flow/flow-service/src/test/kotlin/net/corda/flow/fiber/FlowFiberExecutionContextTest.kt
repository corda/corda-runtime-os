package net.corda.flow.fiber

import net.corda.flow.BOB_X500_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FlowFiberExecutionContextTest {

    @Test
    fun `test member x500 name is taken from holding identity`() {
        val target = FlowFiberExecutionContext(
            mock(),
            mock(),
            mock(),
            mock(),
            net.corda.flow.BOB_X500_HOLDING_IDENTITY,
            mock()
        )

        assertThat(target.memberX500Name).isEqualTo(BOB_X500_NAME)
    }
}