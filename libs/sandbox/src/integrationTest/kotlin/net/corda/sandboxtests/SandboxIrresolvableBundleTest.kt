package net.corda.sandboxtests

import net.corda.sandbox.SandboxException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the inability to resolve against private bundles in a public sandbox. */
@ExtendWith(ServiceExtension::class)
class SandboxIrresolvableBundleTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `bundle cannot resolve against a private bundle in a public sandbox`() {
        val e = assertThrows<SandboxException> {
            sandboxLoader.createSandboxGroupFor("sandbox-irresolvable-cpk-cordapp.cpk")
        }
        assertTrue(e.cause?.message!!.contains("Unable to resolve com.example.sandbox.sandbox-irresolvable-cpk"))
    }
}