package net.corda.membership.impl

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.impl.MemberInfoExtension.Companion.certificate
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.isMgm
import net.corda.membership.impl.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.impl.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.impl.converter.PublicKeyHashConverter
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.cipher.suite.KeyEncodingService
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey

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
        const val TLS_PKI = "tlsPki"
        const val P2P_PROTOCOL_MODE = "p2pProtocolMode"
        const val MGM_INFO = "mgmInfo"
        const val CIPHER_SUITE = "cipherSuite"
        const val ROLES = "roles"

        private const val DEFAULT_KEY = "1234"
        enum class GroupPolicyType {
            STATIC,
            DYNAMIC
        }
    }

    private lateinit var groupPolicyParser: GroupPolicyParser
    private val testGroupId = "ABC123"
    private val defaultKey: PublicKey = mock {
        on { encoded } doReturn DEFAULT_KEY.toByteArray()
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<String>()) } doReturn defaultKey
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(
        listOf(EndpointInfoConverter(), PublicKeyConverter(keyEncodingService), PublicKeyHashConverter())
    )

    @BeforeEach
    fun setUp() {
        groupPolicyParser = GroupPolicyParser(layeredPropertyMapFactory)
    }

    @Test
    fun `Empty string as group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { groupPolicyParser.parse(EMPTY_STRING) }
    }

    @Test
    fun `Whitespace string as group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { groupPolicyParser.parse(WHITESPACE_STRING) }
    }

    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { groupPolicyParser.parse(INVALID_FORMAT_GROUP_POLICY) }
    }

    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = groupPolicyParser.parse(getSampleGroupPolicy(GroupPolicyType.STATIC))
        assertEquals(testGroupId, result.groupId)
    }

    @Test
    fun `Parse group policy - verify internal map`() {
        val result = groupPolicyParser.parse(getSampleGroupPolicy(GroupPolicyType.STATIC))

        // Top level properties
        assertEquals(1, result[FILE_FORMAT_VERSION])
        assertEquals(testGroupId, result.groupId)
        assertEquals("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService", result.registrationProtocol)
        assertEquals("net.corda.v5.mgm.MGMSynchronisationProtocolFactory", result[SYNC_PROTOCOL_FACTORY])
        assertTrue(result[PROTOCOL_PARAMETERS] is Map<*, *>)

        // Protocol parameters
        val protocolParameters = result[PROTOCOL_PARAMETERS] as Map<*, *>
        assertEquals(10, protocolParameters.size)
        assertEquals("Standard", protocolParameters[IDENTITY_PKI])
        assertEquals("Combined", protocolParameters[IDENTITY_KEY_POLICY])

        assertTrue(protocolParameters[IDENTITY_TRUST_STORE] is List<*>)
        assertEquals(2, (protocolParameters[IDENTITY_TRUST_STORE] as List<*>).size)

        assertTrue(protocolParameters[TLS_TRUST_STORE] is List<*>)
        assertEquals(3, (protocolParameters[TLS_TRUST_STORE] as List<*>).size)

        assertEquals("C5", protocolParameters[TLS_PKI])

        assertEquals("AUTHENTICATED_ENCRYPTION", protocolParameters[P2P_PROTOCOL_MODE])

        assertTrue(protocolParameters[MGM_INFO] is Map<*, *>)
        assertEquals(9, (protocolParameters[MGM_INFO] as Map<*, *>).size)

        assertTrue(protocolParameters[CIPHER_SUITE] is Map<*, *>)
        assertEquals(6, (protocolParameters[CIPHER_SUITE] as Map<*, *>).size)

        assertTrue(protocolParameters[ROLES] is Map<*, *>)
        assertEquals(2, (protocolParameters[ROLES] as Map<*, *>).size)

        // Static network
        assertTrue(protocolParameters[STATIC_NETWORK] is Map<*, *>)
        val staticNetwork = protocolParameters[STATIC_NETWORK] as Map<*, *>
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

    @Test
    fun `MGM member info is correctly constructed from group policy information`() {
        val mgmInfo = groupPolicyParser.getMgmInfo(getSampleGroupPolicy(GroupPolicyType.DYNAMIC))!!
        assertSoftly {
            it.assertThat(mgmInfo.name.toString())
                .isEqualTo("CN=Corda Network MGM, OU=MGM, O=Corda Network, L=London, C=GB")
            it.assertThat(mgmInfo.certificate.size).isEqualTo(3)
            it.assertThat(mgmInfo.sessionInitiationKey).isNotNull
            it.assertThat(mgmInfo.ledgerKeys.size).isEqualTo(0)
            it.assertThat(mgmInfo.ledgerKeyHashes.size).isEqualTo(0)
            it.assertThat(mgmInfo.endpoints.size).isEqualTo(2)
            it.assertThat(mgmInfo.platformVersion).isEqualTo(5000)
            it.assertThat(mgmInfo.softwareVersion).isEqualTo("5.0.0")
            it.assertThat(mgmInfo.serial).isEqualTo(1)
            it.assertThat(mgmInfo.isActive).isTrue
            it.assertThat(mgmInfo.isMgm).isTrue
        }
    }

    private fun getSampleGroupPolicy(type: GroupPolicyType) =
        when (type) {
            GroupPolicyType.STATIC -> this::class.java.getResource("/SampleStaticGroupPolicy.json")!!.readText()
            GroupPolicyType.DYNAMIC -> this::class.java.getResource("/SampleDynamicGroupPolicy.json")!!.readText()
        }
}