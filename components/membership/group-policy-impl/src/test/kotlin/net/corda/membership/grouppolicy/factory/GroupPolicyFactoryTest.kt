package net.corda.membership.grouppolicy.factory

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.uncheckedCast
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

        const val x500Name = "x500Name"
        const val keyAlias = "keyAlias"
        const val memberStatus = "memberStatus"
        const val endpointUrl1 = "endpointUrl-1"
        const val endpointProtocol1 = "endpointProtocol-1"
        const val endpointUrl2 = "endpointUrl-2"
        const val endpointProtocol2 = "endpointProtocol-2"
        const val rotatedKeyAlias1 = "rotatedKeyAlias-1"
        const val rotatedKeyAlias2 = "rotatedKeyAlias-2"
        const val fileFormatVersion = "fileFormatVersion"
        const val groupId = "groupId"
        const val registrationProtocolFactory = "registrationProtocolFactory"
        const val synchronisationProtocolFactory = "synchronisationProtocolFactory"
        const val protocolParameters = "protocolParameters"
        const val staticMemberTemplate = "staticMemberTemplate"
        const val identityPKI = "identityPKI"
        const val identityKeyPolicy = "identityKeyPolicy"
        const val identityTrustStore = "identityTrustStore"
        const val tlsTrustStore = "tlsTrustStore"
        const val mgmInfo = "mgmInfo"
        const val cipherSuite = "cipherSuite"
        const val roles = "roles"
    }

    private lateinit var groupPolicyFactory: GroupPolicyFactory
    private val testGroupId = "ABC123"

    @BeforeEach
    fun setUp() {
        groupPolicyFactory = GroupPolicyFactory()
    }

    @Test
    fun `Empty string as group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyFactory.createGroupPolicy(emptyString) }
    }

    @Test
    fun `Whitespace string as group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyFactory.createGroupPolicy(whitespaceString) }
    }

    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyFactory.createGroupPolicy(invalidFormatGroupPolicy) }
    }

    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = groupPolicyFactory.createGroupPolicy(getSampleGroupPolicy())
        assertEquals(testGroupId, result.groupId)
    }

    @Test
    fun `Parse group policy - verify internal map`() {
        val result = groupPolicyFactory.createGroupPolicy(getSampleGroupPolicy())

        // Top level properties
        assertEquals(1, result[fileFormatVersion])
        assertEquals(testGroupId, result[groupId])
        assertEquals("net.corda.v5.mgm.MGMRegistrationProtocolFactory", result[registrationProtocolFactory])
        assertEquals("net.corda.v5.mgm.MGMSynchronisationProtocolFactory", result[synchronisationProtocolFactory])
        assertTrue(result[protocolParameters] is Map<*, *>)
        assertTrue(result[staticMemberTemplate] is List<*>)

        // Protocol parameters
        val protocolParameters = result[protocolParameters] as Map<*, *>
        assertEquals(7, protocolParameters.size)
        assertEquals("Standard", protocolParameters[identityPKI])
        assertEquals("Combined", protocolParameters[identityKeyPolicy])

        assertTrue(protocolParameters[identityTrustStore] is List<*>)
        assertEquals(2, (protocolParameters[identityTrustStore] as List<*>).size)

        assertTrue(protocolParameters[tlsTrustStore] is List<*>)
        assertEquals(3, (protocolParameters[tlsTrustStore] as List<*>).size)

        assertTrue(protocolParameters[mgmInfo] is Map<*, *>)
        assertEquals(9, (protocolParameters[mgmInfo] as Map<*, *>).size)

        assertTrue(protocolParameters[cipherSuite] is Map<*, *>)
        assertEquals(6, (protocolParameters[cipherSuite] as Map<*, *>).size)

        assertTrue(protocolParameters[roles] is Map<*, *>)
        assertEquals(2, (protocolParameters[roles] as Map<*, *>).size)

        // Static member template
        val staticMemberTemplate = result[staticMemberTemplate] as List<*>
        assertEquals(3, staticMemberTemplate.size)

        val alice: Map<String, String> = uncheckedCast(staticMemberTemplate[0])
        assertEquals(6, alice.size)
        assertEquals(alice[x500Name], "C=GB, L=London, O=Alice")
        assertEquals(alice[keyAlias], "alice-alias")
        assertEquals(alice[rotatedKeyAlias1], "alice-historic-alias-1")
        assertEquals(alice[memberStatus], "ACTIVE")
        assertEquals(alice[endpointUrl1], "https://alice.corda5.r3.com:10000")
        assertEquals(alice[endpointProtocol1], 1)

        val bob: Map<String, String> = uncheckedCast(staticMemberTemplate[1])
        assertEquals(7, bob.size)
        assertEquals(bob[x500Name], "C=GB, L=London, O=Bob")
        assertEquals(bob[keyAlias], "bob-alias")
        assertEquals(bob[rotatedKeyAlias1], "bob-historic-alias-1")
        assertEquals(bob[rotatedKeyAlias2], "bob-historic-alias-2")
        assertEquals(bob[memberStatus], "ACTIVE")
        assertEquals(bob[endpointUrl1], "https://bob.corda5.r3.com:10000")
        assertEquals(bob[endpointProtocol1], 1)

        val charlie: Map<String, String> = uncheckedCast(staticMemberTemplate[2])
        assertEquals(7, charlie.size)
        assertEquals(charlie[x500Name], "C=GB, L=London, O=Charlie")
        assertEquals(charlie[keyAlias], "charlie-alias")
        assertEquals(charlie[memberStatus], "SUSPENDED")
        assertEquals(charlie[endpointUrl1], "https://charlie.corda5.r3.com:10000")
        assertEquals(charlie[endpointProtocol1], 1)
        assertEquals(charlie[endpointUrl2], "https://charlie-dr.corda5.r3.com:10001")
        assertEquals(charlie[endpointProtocol2], 1)
    }

    private fun getSampleGroupPolicy(): String {
        val url = this::class.java.getResource("/SampleGroupPolicy.json")
        requireNotNull(url)
        return url.readText()
    }
}