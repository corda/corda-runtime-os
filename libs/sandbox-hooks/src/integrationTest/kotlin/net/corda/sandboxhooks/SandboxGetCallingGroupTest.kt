package net.corda.sandboxhooks

import net.corda.sandbox.SandboxGroup
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `can retrieve the calling sandbox group`() {
        val callingSandboxGroup = applyFunction<Any, SandboxGroup>(
            sandboxLoader.group1, GET_CALLING_SANDBOX_GROUP_FUNCTION, sandboxLoader.sandboxContextService
        )
        assertEquals(sandboxLoader.group1, callingSandboxGroup)
    }
}
