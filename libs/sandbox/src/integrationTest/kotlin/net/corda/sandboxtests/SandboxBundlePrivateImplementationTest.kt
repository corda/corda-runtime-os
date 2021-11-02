package net.corda.sandboxtests

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
    fun `sandbox can invoke a private implementation in a non-exported package of another bundle`() {
        val returnString = runFlow<String>(sandboxLoader.group1, INVOKE_PRIVATE_IMPL_FLOW)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }

    @Test
    fun `sandbox can use a private implementation in a non-exported package of another bundle as a generic`() {
        val returnString = runFlow<String>(sandboxLoader.group1, PRIVATE_IMPL_AS_GENERIC_FLOW)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }
}
