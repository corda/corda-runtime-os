package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.mockBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.UUID.randomUUID

class CpkSandboxImplTests {
    class Example

    @Test
    fun `can load class from main bundles in CPK sandbox`() {
        val mainBundle = mockBundle(klass = Example::class.java)
        val sandbox = CpkSandboxImpl(randomUUID(), mock(), mainBundle, emptySet())

        assertEquals(Example::class.java, sandbox.loadClassFromMainBundle(Example::class.java.name))
    }

    @Test
    fun `cannot load class from other bundles in CPK sandbox`() {
        val mainBundle = mockBundle()
        val otherBundle = mockBundle(klass = Example::class.java)
        val sandbox = CpkSandboxImpl(randomUUID(), mock(), mainBundle, setOf(otherBundle))

        assertThrows<SandboxException> {
            sandbox.loadClassFromMainBundle(Example::class.java.name)
        }
    }

    @Test
    fun `throws if loading class from CPK sandbox with an uninstalled bundle`() {
        val mainBundle = mock<Bundle>().apply {
            whenever(loadClass(any())).thenThrow(IllegalStateException::class.java)
        }
        val sandbox = CpkSandboxImpl(randomUUID(), mock(), mainBundle, emptySet())

        assertThrows<SandboxException> {
            sandbox.loadClassFromMainBundle(Example::class.java.name)
        }
    }
}