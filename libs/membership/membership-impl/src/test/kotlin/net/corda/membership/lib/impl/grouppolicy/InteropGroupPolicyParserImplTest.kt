package net.corda.membership.lib.impl.grouppolicy

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.crypto.impl.converter.PublicKeyHashConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
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
class InteropGroupPolicyParserImplTest {

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
    private val interopGroupPolicyParser = InteropGroupPolicyParserImpl(memberInfoFactory)

    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        "13822f7f-0d2c-450b-8f6f-93c3b8ce9602"
    )

    @Test
    fun `Empty string as group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { interopGroupPolicyParser.parse(holdingIdentity, EMPTY_STRING) { null } }
    }

    @Test
    fun `Whitespace string as group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { interopGroupPolicyParser.parse(holdingIdentity, WHITESPACE_STRING) { null } }
    }

    @Test
    fun `Invalid format group policy throws corda runtime exception`() {
        assertThrows<BadGroupPolicyException> { interopGroupPolicyParser.parse(holdingIdentity, INVALID_FORMAT_GROUP_POLICY) { null } }
    }

    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = interopGroupPolicyParser.parse(holdingIdentity, getSampleGroupPolicy(GroupPolicyType.STATIC)) { null }
        assertEquals(testGroupId, result.groupId)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Parse group policy for member - verify internal map`() {
        val result = interopGroupPolicyParser.parse(holdingIdentity, getSampleGroupPolicy(GroupPolicyType.STATIC)) { null }

        assertSoftly { softly ->
            softly.assertThat(result).isInstanceOf(MemberGroupPolicy::class.java)

            softly.assertThat(result.groupId).isEqualTo(testGroupId)
            softly.assertThat(result.registrationProtocol)
                .isEqualTo("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService")
            softly.assertThat(result.fileFormatVersion).isEqualTo(1)
            softly.assertThat(result.synchronisationProtocol)
                .isEqualTo("net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService")
            softly.assertThat(result.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)
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

    private fun getSampleGroupPolicy(type: GroupPolicyType) =
        when (type) {
            GroupPolicyType.STATIC -> this::class.java.getResource("/InteropSampleGroupPolicy.json")!!.readText()
            GroupPolicyType.DYNAMIC -> this::class.java.getResource("/SampleDynamicGroupPolicy.json")!!.readText()
            GroupPolicyType.MGM -> this::class.java.getResource("/SampleMgmGroupPolicy.json")!!.readText()
        }
}
