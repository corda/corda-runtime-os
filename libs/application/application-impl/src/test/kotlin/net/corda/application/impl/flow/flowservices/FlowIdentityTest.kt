package net.corda.application.impl.flow.flowservices

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.fiber.FlowFiber
import net.corda.flow.manager.fiber.FlowFiberExecutionContext
import net.corda.flow.manager.fiber.FlowFiberService
import net.corda.v5.membership.identity.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowIdentityTest {

    private val flowFiberService = mock<FlowFiberService>()
    private val flowFiberExecutionContext = FlowFiberExecutionContext(
        mock(),
        mock(),
        mock(),
        mock(),
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB","group1")
    )

    private val flowFiber = mock<FlowFiber<*>>()

    @BeforeEach
    fun setup() {
        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        whenever(flowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    @Test
    fun `Test party is returned from current context`() {
         val flowIdentity = FlowIdentityImpl(flowFiberService)

        val expectedIdentity = MemberX500Name(
            commonName = "Alice",
            organisation = "Alice Corp",
            locality = "LDN",
            country = "GB"
        )

        assertThat(flowIdentity.ourIdentity.name.toString()).isEqualTo(expectedIdentity.toString())
    }
}