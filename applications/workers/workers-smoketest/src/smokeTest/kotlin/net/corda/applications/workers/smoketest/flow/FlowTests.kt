package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.X500_SESSION_USER1
import net.corda.applications.workers.smoketest.X500_SESSION_USER2
import net.corda.applications.workers.smoketest.addSoftHsmFor
import net.corda.applications.workers.smoketest.createVirtualNodeFor
import net.corda.applications.workers.smoketest.getFlowClasses
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder

@Suppress("Unused")
@Order(20)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests {

    companion object {

        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
        var charlieHoldingId: String = getHoldingIdShortHash(X500_CHARLIE, GROUP_ID)
        var davidHoldingId: String = getHoldingIdShortHash(X500_DAVID, GROUP_ID)

        val expectedFlows = listOf(
            "net.cordapp.flowworker.development.flows.MessagingFlow",
            "net.cordapp.flowworker.development.flows.PersistenceFlow",
            "net.cordapp.flowworker.development.flows.ReturnAStringFlow",
            "net.cordapp.flowworker.development.flows.RpcSmokeTestFlow",
            "net.cordapp.flowworker.development.flows.TestFlow"
        )
        /*
         * when debugging if you want to run the tests multiple times comment out the @BeforeAll
         * attribute to disable the vnode creation after the first run.
         */
        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {

            val bobActualHoldingId = createVirtualNodeFor(X500_BOB)
            val charlieActualHoldingId = createVirtualNodeFor(X500_CHARLIE)
            val davidActualHoldingId = createVirtualNodeFor(X500_DAVID)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)

            createVirtualNodeFor(X500_SESSION_USER1)
            createVirtualNodeFor(X500_SESSION_USER2)

            addSoftHsmFor(bobHoldingId, "LEDGER")
        }
    }


    @Test
    fun `Get runnable flows for a holdingId`() {
        val flows = getFlowClasses(bobHoldingId)

        assertThat(flows.size).isEqualTo(5)
        assertTrue(flows.containsAll(expectedFlows))
    }

}
