package net.corda.sandboxhooks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the use of non-exported implementation classes from one bundle in another bundle. */
@ExtendWith(ServiceExtension::class)
class SandboxBundlePrivateImplementationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `can invoke private implementation of class`() {
        val returnString = sandboxLoader.runFlow<String>(INVOKE_PRIVATE_IMPL_FLOW, sandboxLoader.group1)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }

    @Test
    fun `can use private implementation of class as generic`() {
        val returnString = sandboxLoader.runFlow<String>(PRIVATE_IMPL_AS_GENERIC_FLOW, sandboxLoader.group1)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }
}
