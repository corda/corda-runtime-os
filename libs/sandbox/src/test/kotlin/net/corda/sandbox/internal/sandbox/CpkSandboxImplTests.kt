package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import java.util.UUID

class CpkSandboxImplTests {
    private fun generateSandboxId() = UUID.randomUUID()
    private val mockBundleUtils = BundleUtils(mock(BundleContext::class.java))

    @Test
    fun `can load class from CorDapp bundles in CPK sandbox`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(String::class.java.name)).thenReturn(String::class.java)
        }
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, emptySet())

        assertEquals(String::class.java, sandbox.loadClassFromCordappBundle(String::class.java.name))
    }

    @Test
    fun `cannot load class from other bundles in CPK sandbox`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(any())).thenThrow(ClassNotFoundException::class.java)
        }
        val otherBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(Int::class.java.name)).thenReturn(Int::class.java)
        }
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, setOf(otherBundle))

        assertNull(sandbox.loadClassFromCordappBundle(Int::class.java.name))
    }

    @Test
    fun `throws if loading class from CPK sandbox with an uninstalled bundle`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(any())).thenThrow(IllegalStateException::class.java)
        }
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, emptySet())

        assertThrows<SandboxException> {
            sandbox.loadClassFromCordappBundle(Int::class.java.name)
        }
    }

    @Test
    fun `correctly indicates whether the CPK sandbox's CorDapp bundle contains a given class`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(String::class.java.name)).thenReturn(String::class.java)
            whenever(loadClass(Int::class.java.name)).thenThrow(ClassNotFoundException::class.java)
            whenever(loadClass(Boolean::class.java.name)).thenThrow(ClassNotFoundException::class.java)
        }
        val otherBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(Int::class.java.name)).thenReturn(Int::class.java)
        }

        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, setOf(otherBundle))

        assertTrue(sandbox.cordappBundleContainsClass(String::class.java.name))
        assertFalse(sandbox.cordappBundleContainsClass(Int::class.java.name))
        assertFalse(sandbox.cordappBundleContainsClass(Boolean::class.java.name))
    }
}