package net.corda.applications.workers.smoketest.rbac

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RbacTestUtils.getAllRbacRoles
import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration

@Order(15)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class RbacTests {

    private companion object {
        private val aliceHoldingId: String = getHoldingIdShortHash(X500_ALICE, GROUP_ID)
    }

    @Test
    fun `check FlowExecutorRole created`() {
        eventually(Duration.ofSeconds(60)) {
            val allRolesNames = getAllRbacRoles().map { it.roleName }
            assertThat(allRolesNames).withFailMessage("Available roles: $allRolesNames}")
                .contains("FlowExecutorRole-$aliceHoldingId")
        }
    }
}