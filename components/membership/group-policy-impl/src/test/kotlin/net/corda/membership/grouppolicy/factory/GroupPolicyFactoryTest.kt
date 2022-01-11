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
        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "                  "
        const val INVALID_FORMAT_GROUP_POLICY = "{{[{[{[{[{[{[ \"groupId\": \"ABC123\" }"

        const val X500_NAME = "x500Name"
        const val KEY_ALIAS = "keyAlias"
        const val MEMBER_STATUS = "memberStatus"
        const val ENDPOINT_URL_1 = "endpointUrl-1"
        const val ENDPOINT_PROTOCOL_1 = "endpointProtocol-1"
        const val ENDPOINT_URL_2 = "endpointUrl-2"
        const val ENDPOINT_PROTOCOL_2 = "endpointProtocol-2"
        const val ROTATED_KEY_ALIAS_1 = "rotatedKeyAlias-1"
        const val ROTATED_KEY_ALIAS_2 = "rotatedKeyAlias-2"
        const val FILE_FORMAT_VERSION = "fileFormatVersion"
        const val GROUP_ID = "groupId"
        const val REGISTRATION_PROTOCOL_FACTORY = "registrationProtocolFactory"
        const val SYNC_PROTOCOL_FACTORY = "synchronisationProtocolFactory"
        const val PROTOCOL_PARAMETERS = "protocolParameters"
        const val STATIC_MEMBER_TEMPLATE = "staticMemberTemplate"
        const val IDENTITY_PKI = "identityPKI"
        const val IDENTITY_KEY_POLICY = "identityKeyPolicy"
        const val IDENTITY_TRUST_STORE = "identityTrustStore"
        const val TLS_TRUST_STORE = "tlsTrustStore"
        const val MGM_INFO = "mgmInfo"
        const val CIPHER_SUITE = "cipherSuite"
        const val ROLES = "roles"
    }

    private lateinit var groupPolicyFactory: GroupPolicyFactory
    private val testGroupId = "ABC123"

    @BeforeEach
    fun setUp() {
        groupPolicyFactory = GroupPolicyFactory()
    }

    @Test
    fun `Empty string as group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyFactory.createGroupPolicy(EMPTY_STRING) }
    }

    @Test
    fun `Whitespace string as group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyFactory.createGroupPolicy(WHITESPACE_STRING) }
    }

    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<CordaRuntimeException> { groupPolicyFactory.createGroupPolicy(INVALID_FORMAT_GROUP_POLICY) }
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
        assertEquals(1, result[FILE_FORMAT_VERSION])
        assertEquals(testGroupId, result[GROUP_ID])
        assertEquals("net.corda.v5.mgm.MGMRegistrationProtocolFactory", result[REGISTRATION_PROTOCOL_FACTORY])
        assertEquals("net.corda.v5.mgm.MGMSynchronisationProtocolFactory", result[SYNC_PROTOCOL_FACTORY])
        assertTrue(result[PROTOCOL_PARAMETERS] is Map<*, *>)
        assertTrue(result[STATIC_MEMBER_TEMPLATE] is List<*>)

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

        // Static member template
        val staticMemberTemplate = result[STATIC_MEMBER_TEMPLATE] as List<*>
        assertEquals(3, staticMemberTemplate.size)

        val alice: Map<String, String> = uncheckedCast(staticMemberTemplate[0])
        assertEquals(6, alice.size)
        assertEquals(alice[X500_NAME], "C=GB, L=London, O=Alice")
        assertEquals(alice[KEY_ALIAS], "alice-alias")
        assertEquals(alice[ROTATED_KEY_ALIAS_1], "alice-historic-alias-1")
        assertEquals(alice[MEMBER_STATUS], "ACTIVE")
        assertEquals(alice[ENDPOINT_URL_1], "https://alice.corda5.r3.com:10000")
        assertEquals(alice[ENDPOINT_PROTOCOL_1], 1)

        val bob: Map<String, String> = uncheckedCast(staticMemberTemplate[1])
        assertEquals(7, bob.size)
        assertEquals(bob[X500_NAME], "C=GB, L=London, O=Bob")
        assertEquals(bob[KEY_ALIAS], "bob-alias")
        assertEquals(bob[ROTATED_KEY_ALIAS_1], "bob-historic-alias-1")
        assertEquals(bob[ROTATED_KEY_ALIAS_2], "bob-historic-alias-2")
        assertEquals(bob[MEMBER_STATUS], "ACTIVE")
        assertEquals(bob[ENDPOINT_URL_1], "https://bob.corda5.r3.com:10000")
        assertEquals(bob[ENDPOINT_PROTOCOL_1], 1)

        val charlie: Map<String, String> = uncheckedCast(staticMemberTemplate[2])
        assertEquals(7, charlie.size)
        assertEquals(charlie[X500_NAME], "C=GB, L=London, O=Charlie")
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