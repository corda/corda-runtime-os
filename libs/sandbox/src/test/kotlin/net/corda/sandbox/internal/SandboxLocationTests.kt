package net.corda.sandbox.internal

import net.corda.sandbox.SandboxException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.util.UUID.randomUUID

class SandboxLocationTests {
    private val validUUID = randomUUID()
    private val validURI = URI("testUri")

    @Test
    fun `sandbox location is stringified correctly`() {
        val sandboxLocation = SandboxLocation(validUUID, validURI)

        assertEquals("sandbox/$validUUID/$validURI", sandboxLocation.toString())
    }

    @Test
    fun `sandbox location is destringified correctly`() {
        val sandboxLocation = SandboxLocation(validUUID, validURI)
        val sandboxLocationFromString = SandboxLocation.fromString(sandboxLocation.toString())

        assertEquals(sandboxLocation, sandboxLocationFromString)
    }

    @Test
    fun `throws if sandbox location has incorrect number of components`() {
        val zeroComponents = ""
        val oneComponent = "sandbox/"
        val twoComponents = "sandbox/$validUUID"
        val fourComponents = "sandbox/$validUUID/$validURI/suffix"

        listOf(zeroComponents, oneComponent, twoComponents, fourComponents).forEach { sandboxLocationString ->
            assertThrows<SandboxException> {
                SandboxLocation.fromString(sandboxLocationString)
            }
        }
    }

    @Test
    fun `throws if sandbox location has incorrect prefix`() {
        assertThrows<SandboxException> {
            SandboxLocation.fromString("notSandbox/$validUUID/$validURI")
        }
    }

    @Test
    fun `throws if sandbox location has invalid UUID`() {
        assertThrows<SandboxException> {
            SandboxLocation.fromString("sandbox/badUUID/$validURI")
        }
    }

    @Test
    fun `throws if sandbox location has invalid URI`() {
        assertThrows<SandboxException> {
            SandboxLocation.fromString("sandbox/$validUUID/${URI("/badUri")}")
        }
    }
}