package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.UUID.randomUUID

class CpkSandboxImplTests {
    @Test
    fun `can load class from CorDapp bundles in CPK sandbox`() {
        val cordappBundle = mock<Bundle>().apply {
            whenever(loadClass(String::class.java.name)).thenReturn(String::class.java)
        }
        val sandbox = CpkSandboxImpl(mock(), randomUUID(), mock(), cordappBundle, emptySet())

        assertEquals(String::class.java, sandbox.loadClassFromCordappBundle(String::class.java.name))
    }

    @Test
    fun `cannot load class from other bundles in CPK sandbox`() {
        val cordappBundle = mock<Bundle>().apply {
            whenever(loadClass(any())).thenThrow(ClassNotFoundException::class.java)
        }
        val otherBundle = mock<Bundle>().apply {
            whenever(loadClass(Int::class.java.name)).thenReturn(Int::class.java)
        }
        val sandbox = CpkSandboxImpl(mock(), randomUUID(), mock(), cordappBundle, setOf(otherBundle))

        assertThrows<SandboxException> {
            sandbox.loadClassFromCordappBundle(Int::class.java.name)
        }
    }

    @Test
    fun `throws if loading class from CPK sandbox with an uninstalled bundle`() {
        val cordappBundle = mock<Bundle>().apply {
            whenever(loadClass(any())).thenThrow(IllegalStateException::class.java)
        }
        val sandbox = CpkSandboxImpl(mock(), randomUUID(), mock(), cordappBundle, emptySet())

        assertThrows<SandboxException> {
            sandbox.loadClassFromCordappBundle(Int::class.java.name)
        }
    }

    @Test
    fun `correctly indicates whether the CPK sandbox's CorDapp bundle contains a given class`() {
        val cordappBundle = mock<Bundle>().apply {
            whenever(loadClass(String::class.java.name)).thenReturn(String::class.java)
            whenever(loadClass(Int::class.java.name)).thenThrow(ClassNotFoundException::class.java)
            whenever(loadClass(Boolean::class.java.name)).thenThrow(ClassNotFoundException::class.java)
        }
        val otherBundle = mock<Bundle>().apply {
            whenever(loadClass(Int::class.java.name)).thenReturn(Int::class.java)
        }

        val sandbox = CpkSandboxImpl(mock(), randomUUID(), mock(), cordappBundle, setOf(otherBundle))

        assertTrue(sandbox.cordappBundleContainsClass(String::class.java.name))
        assertFalse(sandbox.cordappBundleContainsClass(Int::class.java.name))
        assertFalse(sandbox.cordappBundleContainsClass(Boolean::class.java.name))
    }
}