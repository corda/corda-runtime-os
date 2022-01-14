package net.corda.membership.grouppolicy.integration

import net.corda.lifecycle.Lifecycle
import net.corda.membership.grouppolicy.GroupPolicyProviderImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class GroupPolicyProviderIntegrationTest {

    @InjectService(timeout = 5000L)
    lateinit var groupPolicyProvider: GroupPolicyProviderImpl

    @BeforeEach
    fun setUp() {
        (groupPolicyProvider as Lifecycle).start()

        var sleepCount = 0
        while (!groupPolicyProvider.isRunning && sleepCount < 10) {
            Thread.sleep(500)
            sleepCount++
        }
    }

    @Test
    fun `Exception thrown when retrieving group policy for non-existent holding identity`() {
        assertThrows<CordaRuntimeException> {
            groupPolicyProvider.getGroupPolicy(HoldingIdentity("", ""))
        }
    }
}