package net.corda.membership.impl

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.membership.impl.MemberInfoExtension.Companion.certificate
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.isMgm
import net.corda.membership.impl.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.impl.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.impl.converter.PublicKeyHashConverter
import net.corda.v5.cipher.suite.KeyEncodingService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.assertEquals
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

        private const val DEFAULT_KEY = "1234"
        enum class GroupPolicyType {
            STATIC,
            DYNAMIC
        }
    }

    private val testGroupId = "7c5d6948-e17b-44e7-9d1c-fa4a3f667cad"
    private val defaultKey: PublicKey = mock {
        on { encoded } doReturn DEFAULT_KEY.toByteArray()
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<String>()) } doReturn defaultKey
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(
        listOf(EndpointInfoConverter(), PublicKeyConverter(keyEncodingService), PublicKeyHashConverter())
    )
    private val groupPolicyParser = GroupPolicyParser(layeredPropertyMapFactory)

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
    @Suppress("UNCHECKED_CAST")
    fun `Parse group policy - verify internal map`() {
        val result = groupPolicyParser.parse(getSampleGroupPolicy(GroupPolicyType.STATIC))

        assertSoftly { softly ->
            softly.assertThat(result.groupId).isEqualTo(testGroupId)
            softly.assertThat(result.registrationProtocol)
                .isEqualTo("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService")
            softly.assertThat(result)
                .containsEntry("fileFormatVersion", 1)
                .containsEntry("synchronisationProtocol", "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService")
                .hasEntrySatisfying("protocolParameters") { protocolParameters ->
                    assertThat(protocolParameters as? Map<String, Any?>)
                        .isNotNull
                        .containsEntry("sessionKeyPolicy", "Combined")
                        .hasEntrySatisfying("staticNetwork") { staticNetwork ->
                            assertThat(staticNetwork as? Map<String, Any?>)
                                .isNotNull
                                .hasEntrySatisfying("members") { members ->
                                    assertThat(members as? Collection<Any?>)
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
                                }
                        }
                }
                .hasEntrySatisfying("p2pParameters") { p2pParameters ->
                    val certificate = "-----BEGIN CERTIFICATE-----\n{truncated for readability}\n-----END CERTIFICATE-----"
                    assertThat(p2pParameters as? Map<String, Any?>)
                        .isNotNull
                        .containsEntry("sessionTrustRoots", listOf(certificate, certificate))
                        .containsEntry("tlsTrustRoots", listOf(certificate, certificate, certificate))
                        .containsEntry("sessionPki", "Standard")
                        .containsEntry("tlsPki", "Standard")
                        .containsEntry("tlsVersion", "1.3")
                        .containsEntry("protocolMode", "Authentication_Encryption")
                }
                .hasEntrySatisfying("cipherSuite") { cipherSuite ->
                    assertThat(cipherSuite as? Map<String, Any?>)
                        .isNotNull
                        .containsEntry("corda.provider", "default")
                        .containsEntry("corda.signature.provider", "default")
                        .containsEntry("corda.signature.default", "ECDSA_SECP256K1_SHA256")
                        .containsEntry("corda.signature.FRESH_KEYS", "ECDSA_SECP256K1_SHA256")
                        .containsEntry("corda.digest.default", "SHA256")
                        .containsEntry("corda.cryptoservice.provider", "default")
                }
        }
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
