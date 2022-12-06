package net.corda.membership.lib.impl.grouppolicy

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.crypto.impl.converter.PublicKeyHashConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.MemberInfoExtension.Companion.certificate
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.DISTINCT
import net.corda.membership.lib.grouppolicy.MGMGroupPolicy
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.impl.grouppolicy.v1.TEST_CERT
import net.corda.membership.lib.impl.grouppolicy.v1.TEST_FILE_FORMAT_VERSION
import net.corda.membership.lib.impl.grouppolicy.v1.TEST_GROUP_ID
import net.corda.membership.lib.impl.grouppolicy.v1.buildPersistedProperties
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey

/**
 * Unit tests for [GroupPolicyParserImpl]
 */
class GroupPolicyParserImplTest {

    companion object {
        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "                  "
        const val INVALID_FORMAT_GROUP_POLICY = "{{[{[{[{[{[{[ \"groupId\": \"ABC123\" }"

        private const val DEFAULT_KEY = "1234"

        enum class GroupPolicyType {
            STATIC,
            DYNAMIC,
            MGM
        }
    }

    private val testGroupId = "7c5d6948-e17b-44e7-9d1c-fa4a3f667cad"
    private val defaultKey: PublicKey = mock {
        on { encoded } doReturn DEFAULT_KEY.toByteArray()
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<String>()) } doReturn defaultKey
    }
    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(
        listOf(
            EndpointInfoConverter(),
            MemberNotaryDetailsConverter(keyEncodingService),
            PublicKeyConverter(keyEncodingService),
            PublicKeyHashConverter()
        )
    )
    private val memberInfoFactory = MemberInfoFactoryImpl(layeredPropertyMapFactory)
    private val groupPolicyParser = GroupPolicyParserImpl(memberInfoFactory)
    private val persistedProperties = buildPersistedProperties(layeredPropertyMapFactory)

    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        "13822f7f-0d2c-450b-8f6f-93c3b8ce9602"
    )

    @Test
    fun `Empty string as group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { groupPolicyParser.parse(holdingIdentity, EMPTY_STRING) { null } }
    }

    @Test
    fun `Whitespace string as group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { groupPolicyParser.parse(holdingIdentity, WHITESPACE_STRING) { null } }
    }

    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { groupPolicyParser.parse(holdingIdentity, INVALID_FORMAT_GROUP_POLICY) { null } }
    }

    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = groupPolicyParser.parse(holdingIdentity, getSampleGroupPolicy(GroupPolicyType.STATIC)) { null }
        assertEquals(testGroupId, result.groupId)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Parse group policy for member - verify internal map`() {
        val result = groupPolicyParser.parse(holdingIdentity, getSampleGroupPolicy(GroupPolicyType.STATIC)) { null }

        assertSoftly { softly ->
            softly.assertThat(result).isInstanceOf(MemberGroupPolicy::class.java)

            softly.assertThat(result.groupId).isEqualTo(testGroupId)
            softly.assertThat(result.registrationProtocol)
                .isEqualTo("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService")
            softly.assertThat(result.fileFormatVersion).isEqualTo(1)
            softly.assertThat(result.synchronisationProtocol)
                .isEqualTo("net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService")
            softly.assertThat(result.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)
            softly.assertThat(result.protocolParameters.staticNetworkMembers)
                .isNotNull
                .containsExactly(
                    mapOf(
                        "name" to "C=GB, L=London, O=Alice",
                        "memberStatus" to "ACTIVE",
                        "endpointUrl-1" to "https://alice.corda5.r3.com:10000",
                        "endpointProtocol-1" to 1,
                    ),
                    mapOf(
                        "name" to "C=GB, L=London, O=Bob",
                        "memberStatus" to "ACTIVE",
                        "endpointUrl-1" to "https://bob.corda5.r3.com:10000",
                        "endpointProtocol-1" to 1,
                    ),
                    mapOf(
                        "name" to "C=GB, L=London, O=Charlie",
                        "memberStatus" to "SUSPENDED",
                        "endpointUrl-1" to "https://charlie.corda5.r3.com:10000",
                        "endpointProtocol-1" to 1,
                        "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                        "endpointProtocol-2" to 1,
                    ),
                )
            softly.assertThat(result.p2pParameters.sessionTrustRoots).hasSize(3)
            softly.assertThat(result.p2pParameters.tlsTrustRoots).hasSize(4)
            softly.assertThat(result.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD)
            softly.assertThat(result.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD)
            softly.assertThat(result.p2pParameters.tlsVersion).isEqualTo(TlsVersion.VERSION_1_3)
            softly.assertThat(result.p2pParameters.protocolMode).isEqualTo(ProtocolMode.AUTH_ENCRYPT)

            softly.assertThat(result.cipherSuite)
                .containsEntry("corda.provider", "default")
                .containsEntry("corda.signature.provider", "default")
                .containsEntry("corda.signature.default", "ECDSA_SECP256K1_SHA256")
                .containsEntry("corda.signature.FRESH_KEYS", "ECDSA_SECP256K1_SHA256")
                .containsEntry("corda.digest.default", "SHA256")
                .containsEntry("corda.cryptoservice.provider", "default")
        }
    }

    @Test
    fun `Parse group policy for MGM - verify internal map`() {
        val result = groupPolicyParser.parse(holdingIdentity, getSampleGroupPolicy(GroupPolicyType.MGM)) { persistedProperties }

        assertSoftly {
            it.assertThat(result).isInstanceOf(MGMGroupPolicy::class.java)

            it.assertThat(result.fileFormatVersion).isEqualTo(TEST_FILE_FORMAT_VERSION)
            it.assertThat(result.groupId).isEqualTo(TEST_GROUP_ID)
            it.assertThat(result.registrationProtocol)
                .isEqualTo("net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService")
            it.assertThat(result.synchronisationProtocol)
                .isEqualTo("net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl")
            it.assertThat(result.protocolParameters.sessionKeyPolicy).isEqualTo(DISTINCT)
            it.assertThat(result.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD_EV3)
            it.assertThat(result.p2pParameters.sessionTrustRoots).isNotNull
            it.assertThat(result.p2pParameters.sessionTrustRoots?.size).isEqualTo(1)
            it.assertThat(result.p2pParameters.sessionTrustRoots?.first()).isEqualTo(TEST_CERT)
            it.assertThat(result.p2pParameters.tlsTrustRoots.size).isEqualTo(1)
            it.assertThat(result.p2pParameters.tlsTrustRoots.first()).isEqualTo(TEST_CERT)
            it.assertThat(result.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD_EV3)
            it.assertThat(result.p2pParameters.tlsVersion).isEqualTo(TlsVersion.VERSION_1_2)
            it.assertThat(result.p2pParameters.protocolMode).isEqualTo(ProtocolMode.AUTH)

            it.assertThat(result.mgmInfo).isNull()
            it.assertThat(result.cipherSuite.entries).isEmpty()
        }
    }

    @Test
    fun `MGM member info is correctly constructed from group policy information`() {
        val mgmInfo = groupPolicyParser.getMgmInfo(holdingIdentity, getSampleGroupPolicy(GroupPolicyType.DYNAMIC))!!
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
            GroupPolicyType.MGM -> this::class.java.getResource("/SampleMgmGroupPolicy.json")!!.readText()
        }
}
