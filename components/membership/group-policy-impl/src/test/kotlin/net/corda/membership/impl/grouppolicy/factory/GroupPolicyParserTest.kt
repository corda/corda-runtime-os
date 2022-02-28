package net.corda.membership.impl.grouppolicy.factory

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [GroupPolicyParser]
 */
class GroupPolicyParserTest {

    companion object {
        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "                  "
        const val INVALID_FORMAT_GROUP_POLICY = "{{[{[{[{[{[{[ \"groupId\": \"ABC123\" }"

        const val NAME = "name"
        const val KEY_ALIAS = "keyAlias"
        const val MEMBER_STATUS = "memberStatus"
        const val ENDPOINT_URL_1 = "endpointUrl-1"
        const val ENDPOINT_PROTOCOL_1 = "endpointProtocol-1"
        const val ENDPOINT_URL_2 = "endpointUrl-2"
        const val ENDPOINT_PROTOCOL_2 = "endpointProtocol-2"
        const val ROTATED_KEY_ALIAS_1 = "rotatedKeyAlias-1"
        const val ROTATED_KEY_ALIAS_2 = "rotatedKeyAlias-2"
        const val FILE_FORMAT_VERSION = "fileFormatVersion"
        const val SYNC_PROTOCOL_FACTORY = "synchronisationProtocolFactory"
        const val PROTOCOL_PARAMETERS = "protocolParameters"
        const val STATIC_NETWORK = "staticNetwork"
        const val STATIC_MEMBERS = "members"
        const val IDENTITY_PKI = "identityPKI"
        const val IDENTITY_KEY_POLICY = "identityKeyPolicy"
        const val IDENTITY_TRUST_STORE = "identityTrustStore"
        const val TLS_TRUST_STORE = "tlsTrustStore"
        const val MGM_INFO = "mgmInfo"
        const val CIPHER_SUITE = "cipherSuite"
        const val ROLES = "roles"
    }

    private lateinit var groupPolicyParser: GroupPolicyParser
    private val testGroupId = "ABC123"

    @BeforeEach
    fun setUp() {
        groupPolicyParser = GroupPolicyParser()
    }

    @Test
    fun `Empty string as group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyParser.parse(EMPTY_STRING) }
    }

    @Test
    fun `Whitespace string as group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyParser.parse(WHITESPACE_STRING) }
    }

    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyParser.parse(INVALID_FORMAT_GROUP_POLICY) }
    }

    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = groupPolicyParser.parse(getSampleGroupPolicy())
        assertEquals(testGroupId, result.groupId)
    }

    @Test
    fun `Parse group policy - verify internal map`() {
        val result = groupPolicyParser.parse(getSampleGroupPolicy())

        // Top level properties
        assertEquals(1, result[FILE_FORMAT_VERSION])
        assertEquals(testGroupId, result.groupId)
        assertEquals("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService", result.registrationProtocol)
        assertEquals("net.corda.v5.mgm.MGMSynchronisationProtocolFactory", result[SYNC_PROTOCOL_FACTORY])
        assertTrue(result[PROTOCOL_PARAMETERS] is Map<*, *>)
        assertTrue(result[STATIC_NETWORK] is Map<*, *>)

        // Protocol parameters
        val protocolParameters = result[PROTOCOL_PARAMETERS] as Map<*, *>
        assertEquals(7, protocolParameters.size)
        assertEquals("Standard", protocolParameters[IDENTITY_PKI])
        assertEquals("Combined", protocolParameters[IDENTITY_KEY_POLICY])

        assertTrue(protocolParameters[IDENTITY_TRUST_STORE] is List<*>)
        assertEquals(2, (protocolParameters[IDENTITY_TRUST_STORE] as List<*>).size)

        assertTrue(protocolParameters[TLS_TRUST_STORE] is List<*>)
        assertEquals(3, (protocolParameters[TLS_TRUST_STORE] as List<*>).size)

        assertTrue(protocolParameters[MGM_INFO] is Map<*, *>)
        assertEquals(9, (protocolParameters[MGM_INFO] as Map<*, *>).size)

        assertTrue(protocolParameters[CIPHER_SUITE] is Map<*, *>)
        assertEquals(6, (protocolParameters[CIPHER_SUITE] as Map<*, *>).size)

        assertTrue(protocolParameters[ROLES] is Map<*, *>)
        assertEquals(2, (protocolParameters[ROLES] as Map<*, *>).size)

        // Static network
        val staticNetwork = result[STATIC_NETWORK] as Map<*, *>
        assertEquals(2, staticNetwork.size)

        val staticMembers = staticNetwork[STATIC_MEMBERS] as List<*>

        val alice: Map<String, String> = uncheckedCast(staticMembers[0])
        assertEquals(6, alice.size)
        assertEquals(alice[NAME], "C=GB, L=London, O=Alice")
        assertEquals(alice[KEY_ALIAS], "alice-alias")
        assertEquals(alice[ROTATED_KEY_ALIAS_1], "alice-historic-alias-1")
        assertEquals(alice[MEMBER_STATUS], "ACTIVE")
        assertEquals(alice[ENDPOINT_URL_1], "https://alice.corda5.r3.com:10000")
        assertEquals(alice[ENDPOINT_PROTOCOL_1], 1)

        val bob: Map<String, String> = uncheckedCast(staticMembers[1])
        assertEquals(7, bob.size)
        assertEquals(bob[NAME], "C=GB, L=London, O=Bob")
        assertEquals(bob[KEY_ALIAS], "bob-alias")
        assertEquals(bob[ROTATED_KEY_ALIAS_1], "bob-historic-alias-1")
        assertEquals(bob[ROTATED_KEY_ALIAS_2], "bob-historic-alias-2")
        assertEquals(bob[MEMBER_STATUS], "ACTIVE")
        assertEquals(bob[ENDPOINT_URL_1], "https://bob.corda5.r3.com:10000")
        assertEquals(bob[ENDPOINT_PROTOCOL_1], 1)

        val charlie: Map<String, String> = uncheckedCast(staticMembers[2])
        assertEquals(7, charlie.size)
        assertEquals(charlie[NAME], "C=GB, L=London, O=Charlie")
        assertEquals(charlie[KEY_ALIAS], "charlie-alias")
        assertEquals(charlie[MEMBER_STATUS], "SUSPENDED")
        assertEquals(charlie[ENDPOINT_URL_1], "https://charlie.corda5.r3.com:10000")
        assertEquals(charlie[ENDPOINT_PROTOCOL_1], 1)
        assertEquals(charlie[ENDPOINT_URL_2], "https://charlie-dr.corda5.r3.com:10001")
        assertEquals(charlie[ENDPOINT_PROTOCOL_2], 1)
    }

    private fun getSampleGroupPolicy(): String {
        val url = this::class.java.getResource("/SampleGroupPolicy.json")
        requireNotNull(url)
        return url.readText()
    }
}