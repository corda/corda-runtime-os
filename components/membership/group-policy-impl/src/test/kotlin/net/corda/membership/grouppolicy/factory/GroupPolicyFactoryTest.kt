package net.corda.membership.grouppolicy.factory

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [GroupPolicyFactory]
 */
class GroupPolicyFactoryTest {

    companion object {
        const val emptyString = ""
        const val whitespaceString = "                  "
        const val invalidFormatGroupPolicy = "{{[{[{[{[{[{[ \"groupId\": \"ABC123\" }"
    }

    private lateinit var groupPolicyFactory: GroupPolicyFactory
    private val testGroupId = "ABC123"

    @BeforeEach
    fun setUp() {
        groupPolicyFactory = GroupPolicyFactory()
    }

    /**
     * Test that an empty string passed in for the group policy doesn't throw an exception but instead results in an
     * empty map.
     */
    @Test
    fun `Empty string as group policy returns empty group policy`() {
        val result = groupPolicyFactory.createGroupPolicy(emptyString)
        assertEquals(0, result.size)
    }

    /**
     * Test that a string of whitespace passed in for the group policy doesn't throw an exception but instead results
     * in an empty map.
     */
    @Test
    fun `Whitespace string as group policy returns empty group policy`() {
        val result = groupPolicyFactory.createGroupPolicy(whitespaceString)
        assertEquals(0, result.size)
    }

    /**
     * Test that invalid JSON passed in for the group policy throws a corda runtime exception.
     */
    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> {
            groupPolicyFactory.createGroupPolicy(invalidFormatGroupPolicy)
        }
    }

    /**
     * Parse a full group policy sample and check the public interface properties are as expected.
     */
    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = groupPolicyFactory.createGroupPolicy(getSampleGroupPolicy())
        assertEquals(testGroupId, result.groupId)
    }

    /**
     * Parse a full group policy sample and check that the internal map values are as expected.
     * This test only verifies two levels rather than every single value.
     */
    @Test
    fun `Parse group policy - verify internal map`() {
        val result = groupPolicyFactory.createGroupPolicy(getSampleGroupPolicy())

        // Top level properties
        assertEquals(1, result["fileFormatVersion"])
        assertEquals(testGroupId, result["groupId"])
        assertEquals("net.corda.v5.mgm.MGMRegistrationProtocolFactory", result["registrationProtocolFactory"])
        assertEquals("net.corda.v5.mgm.MGMSynchronisationProtocolFactory", result["synchronisationProtocolFactory"])
        assertTrue(result["protocolParameters"] is Map<*, *>)
        assertTrue(result["staticMemberTemplate"] is List<*>)

        // Protocol parameters
        val protocolParameters = result["protocolParameters"] as Map<*, *>
        assertEquals(7, protocolParameters.size)
        assertEquals("Standard", protocolParameters["identityPKI"])
        assertEquals("Combined", protocolParameters["identityKeyPolicy"])

        assertTrue(protocolParameters["identityTrustStore"] is List<*>)
        assertEquals(2, (protocolParameters["identityTrustStore"] as List<*>).size)

        assertTrue(protocolParameters["tlsTrustStore"] is List<*>)
        assertEquals(3, (protocolParameters["tlsTrustStore"] as List<*>).size)

        assertTrue(protocolParameters["mgmInfo"] is Map<*, *>)
        assertEquals(9, (protocolParameters["mgmInfo"] as Map<*, *>).size)

        assertTrue(protocolParameters["cipherSuite"] is Map<*, *>)
        assertEquals(6, (protocolParameters["cipherSuite"] as Map<*, *>).size)

        assertTrue(protocolParameters["roles"] is Map<*, *>)
        assertEquals(2, (protocolParameters["roles"] as Map<*, *>).size)

        // Static member template
        val staticMemberTemplate = result["staticMemberTemplate"] as List<*>
        assertEquals(3, staticMemberTemplate.size)
    }

    fun getSampleGroupPolicy(): String {
        val url = this::class.java.getResource("/SampleGroupPolicy.json")
        requireNotNull(url)
        return url.readText()
    }
}