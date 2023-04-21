package net.corda.sandbox.internal

import net.corda.sandbox.SandboxException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID.randomUUID

class SandboxLocationTests {
    private val validSecurityDomain = "secDomain"
    private val validUUID = randomUUID()
    private val validURI = "testUri"

    @Test
    fun `sandbox location is stringified correctly`() {
        val sandboxLocation = SandboxLocation(validSecurityDomain, validUUID, validURI)

        assertEquals("$validSecurityDomain/$validUUID/$validURI", sandboxLocation.toString())
    }

    @Test
    fun `sandbox location is destringified correctly`() {
        val sandboxLocation = SandboxLocation(validSecurityDomain, validUUID, validURI)
        val sandboxLocationFromString = SandboxLocation.fromString("$validSecurityDomain/$validUUID/$validURI")

        assertEquals(sandboxLocation, sandboxLocationFromString)
    }

    @Test
    fun `can handle sandbox location where the final component contains forward slashes`() {
        val source = "separated/by/slashes"
        val sandboxLocation = SandboxLocation(validSecurityDomain, validUUID, source)
        val sandboxLocationFromString = SandboxLocation.fromString("$validSecurityDomain/$validUUID/$source")

        assertEquals(sandboxLocation, sandboxLocationFromString)
    }

    @Test
    fun `throws if sandbox location has insufficient components`() {
        val zeroComponents = ""
        val oneComponent = "sandbox/"
        val twoComponents = "sandbox/$validUUID"

        listOf(zeroComponents, oneComponent, twoComponents).forEach { sandboxLocationString ->
            assertThrows<SandboxException> {
                SandboxLocation.fromString(sandboxLocationString)
            }
        }
    }

    @Test
    fun `throws if sandbox location has invalid UUID`() {
        assertThrows<SandboxException> {
            SandboxLocation.fromString("sandbox/badUUID/$validURI")
        }
    }
}