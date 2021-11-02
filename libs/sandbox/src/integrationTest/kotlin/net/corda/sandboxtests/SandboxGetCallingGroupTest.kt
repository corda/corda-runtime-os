package net.corda.sandboxtests

import net.corda.sandbox.SandboxGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the ability to retrieve the calling sandbox group. */
@ExtendWith(ServiceExtension::class)
class SandboxGetCallingGroupTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `can retrieve the calling sandbox group from within a sandbox`() {
        val callingSandboxGroup = applyFunction<Any, SandboxGroup>(
            sandboxLoader.group1, GET_CALLING_SANDBOX_GROUP_FUNCTION, sandboxLoader.sandboxContextService
        )
        assertEquals(sandboxLoader.group1, callingSandboxGroup)
    }

    @Test
    fun `retrieving the calling sandbox group from outside a sandbox returns null`() {
        assertNull(sandboxLoader.sandboxContextService.getCallingSandboxGroup())
    }
}
