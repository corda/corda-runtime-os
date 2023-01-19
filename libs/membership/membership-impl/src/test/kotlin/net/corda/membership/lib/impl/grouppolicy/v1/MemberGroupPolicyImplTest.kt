package net.corda.membership.lib.impl.grouppolicy.v1

import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.StaticNetwork.MEMBERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.impl.grouppolicy.getBadCertError
import net.corda.membership.lib.impl.grouppolicy.getBadEnumError
import net.corda.membership.lib.impl.grouppolicy.getBlankValueError
import net.corda.membership.lib.impl.grouppolicy.getMissingCertError
import net.corda.membership.lib.impl.grouppolicy.getMissingKeyError
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class MemberGroupPolicyImplTest {

    companion object {
        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "   "
        const val BAD_ENUM_VALUE = "BAD_VALUE_99&!!"
    }

    @Nested
    inner class StaticNetworkPolicyTests {
        @Test
        fun `complete static network group policy can be parsed correctly`() {
            val groupPolicy: GroupPolicy = assertDoesNotThrow {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        mgmInfoOverride = null
                    )
                )
            }

            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(TEST_FILE_FORMAT_VERSION)
                it.assertThat(groupPolicy.groupId).isEqualTo(TEST_GROUP_ID)
                it.assertThat(groupPolicy.registrationProtocol).isEqualTo(TEST_REG_PROTOCOL)
                it.assertThat(groupPolicy.synchronisationProtocol).isEqualTo(TEST_SYNC_PROTOCOL)

                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)
                it.assertThat(groupPolicy.protocolParameters.staticNetworkMembers)
                    .isNotNull
                    .isNotEmpty
                    .hasSize(2)
                    .containsExactly(
                        mapOf(TEST_STATIC_MEMBER_KEY to TEST_STATIC_MEMBER_VALUE),
                        mapOf(TEST_STATIC_MEMBER_KEY to TEST_STATIC_MEMBER_VALUE)
                    )

                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots)
                    .isNotNull
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_3)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH_ENCRYPT)

                it.assertThat(groupPolicy.mgmInfo).isNull()

                it.assertThat(groupPolicy.cipherSuite.entries)
                    .isNotEmpty
                    .hasSize(1)
                    .contains(
                        entry(TEST_CIPHER_SUITE_KEY, TEST_CIPHER_SUITE_VAL)
                    )
            }
        }

        @Test
        fun `session trust roots can be null if pki mode is NoPKI`() {
            assertDoesNotThrow {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = SessionPkiMode.NO_PKI.toString(),
                            sessionTrustRootOverride = null
                        ),
                        mgmInfoOverride = null
                    )
                )
            }
        }
    }

    @Nested
    inner class DynamicNetworkPolicyTests {
        @Test
        fun `complete dynamic network group policy can be parsed correctly`() {
            val groupPolicy: GroupPolicy = assertDoesNotThrow {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            staticNetworkOverride = null
                        )
                    )
                )
            }

            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(TEST_FILE_FORMAT_VERSION)
                it.assertThat(groupPolicy.groupId).isEqualTo(TEST_GROUP_ID)
                it.assertThat(groupPolicy.registrationProtocol).isEqualTo(TEST_REG_PROTOCOL)
                it.assertThat(groupPolicy.synchronisationProtocol).isEqualTo(TEST_SYNC_PROTOCOL)

                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)
                it.assertThat(groupPolicy.protocolParameters.staticNetworkMembers).isNull()

                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots)
                    .isNotNull
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_3)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH_ENCRYPT)

                it.assertThat(groupPolicy.mgmInfo)
                    .isNotNull
                    .hasSize(2)
                    .contains(
                        entry(TEST_MGM_INFO_STRING_KEY, TEST_MGM_INFO_STRING_VAL),
                        entry(TEST_MGM_INFO_INT_KEY, TEST_MGM_INFO_INT_VAL.toString())
                    )

                it.assertThat(groupPolicy.cipherSuite.entries)
                    .isNotEmpty
                    .hasSize(1)
                    .contains(
                        entry(TEST_CIPHER_SUITE_KEY, TEST_CIPHER_SUITE_VAL)
                    )
            }
        }

        @Test
        fun `session trust roots can be null if pki mode is NoPKI`() {
            assertDoesNotThrow {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = SessionPkiMode.NO_PKI.toString(),
                            sessionTrustRootOverride = null
                        ),
                        protocolParametersOverride = buildProtocolParameters(
                            staticNetworkOverride = null
                        )
                    )
                )
            }
        }

        @Test
        fun `mgm client certificate subject is null by default`() {
            val groupPolicy = MemberGroupPolicyImpl(
                buildGroupPolicyNode()
            )

            assertThat(groupPolicy.p2pParameters.mgmClientCertificateSubject).isNull()
        }

        @Test
        fun `mgm client certificate subject is read correctly`() {
            val subject = "O=One, C=GB, L = Three"
            val groupPolicy = MemberGroupPolicyImpl(
                buildGroupPolicyNode(
                    p2pParametersOverride = buildP2PParameters(
                        mgmClientCertificateSubject = subject
                    )
                )
            )

            assertThat(groupPolicy.p2pParameters.mgmClientCertificateSubject).isEqualTo(MemberX500Name.parse(subject))
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `file format version must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(fileFormatVersionOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(FILE_FORMAT_VERSION))
        }

        @Test
        fun `group policy ID must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(groupIdOverride = EMPTY_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(GROUP_ID))
        }

        @Test
        fun `group policy ID must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(groupIdOverride = WHITESPACE_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(GROUP_ID))
        }

        @Test
        fun `group policy ID must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(groupIdOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(GROUP_ID))
        }

        @Test
        fun `registration protocol must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(registrationProtocolOverride = EMPTY_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `registration protocol must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(registrationProtocolOverride = WHITESPACE_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `registration protocol must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(registrationProtocolOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(syncProtocolOverride = EMPTY_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SYNC_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(syncProtocolOverride = WHITESPACE_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SYNC_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(syncProtocolOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SYNC_PROTOCOL))
        }

        @Test
        fun `protocol parameters must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(protocolParametersOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(PROTOCOL_PARAMETERS))
        }

        @Test
        fun `session key policy must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SESSION_KEY_POLICY))
        }

        @Test
        fun `session key policy must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_KEY_POLICY))
        }

        @Test
        fun `session key policy must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_KEY_POLICY))
        }

        @Test
        fun `session key policy must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(SESSION_KEY_POLICY, BAD_ENUM_VALUE))
        }

        @Test
        fun `static member list must be present if static network is present`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            staticNetworkOverride = buildStaticMemberTemplate(null)
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(MEMBERS))
        }

        @Test
        fun `p2p parameters must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(p2pParametersOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(P2P_PARAMETERS))
        }

        @Test
        fun `session pki must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SESSION_PKI))
        }

        @Test
        fun `session pki must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_PKI))
        }

        @Test
        fun `session pki must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_PKI))
        }

        @Test
        fun `session pki must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(SESSION_PKI, BAD_ENUM_VALUE))
        }

        @Test
        fun `session trust roots must be non null if pki mode is not NoPKI`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SESSION_TRUST_ROOTS))
        }

        @Test
        fun `session trust roots must contain at least one cert if pki mode is not NoPKI`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = emptyList()
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingCertError(SESSION_TRUST_ROOTS))
        }

        @Test
        fun `session trust roots must contain valid certificates`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = listOf(UNPARSEABLE_CERT)
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(SESSION_TRUST_ROOTS, 0))
        }

        @Test
        fun `session trust roots with a mix of valid and invalid certificates fails`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = listOf(
                                R3_COM_CERT,
                                UNPARSEABLE_CERT
                            )
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(SESSION_TRUST_ROOTS, 1))
        }

        @Test
        fun `tls trust roots must be non null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(TLS_TRUST_ROOTS))
        }

        @Test
        fun `tls trust roots must contain at least one cert`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = emptyList()
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingCertError(TLS_TRUST_ROOTS))
        }

        @Test
        fun `tls trust roots must contain valid certificates`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = listOf(UNPARSEABLE_CERT)
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(TLS_TRUST_ROOTS, 0))
        }

        @Test
        fun `tls trust roots with a mix of valid and invalid certificates fails`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = listOf(
                                R3_COM_CERT,
                                UNPARSEABLE_CERT
                            )
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(TLS_TRUST_ROOTS, 1))
        }

        @Test
        fun `tls pki must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(TLS_PKI))
        }

        @Test
        fun `tls pki must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_PKI))
        }

        @Test
        fun `tls pki must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_PKI))
        }

        @Test
        fun `tls pki must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(TLS_PKI, BAD_ENUM_VALUE))
        }

        @Test
        fun `tls version must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(TLS_VERSION))
        }

        @Test
        fun `tls version must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_VERSION))
        }

        @Test
        fun `tls version must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_VERSION))
        }

        @Test
        fun `tls version must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(TLS_VERSION, BAD_ENUM_VALUE))
        }

        @Test
        fun `protocol mode must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(PROTOCOL_MODE))
        }

        @Test
        fun `protocol mode must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(PROTOCOL_MODE))
        }

        @Test
        fun `protocol mode must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(PROTOCOL_MODE))
        }

        @Test
        fun `protocol mode must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(PROTOCOL_MODE, BAD_ENUM_VALUE))
        }

        @Test
        fun `mgm info can be null`() {
            assertDoesNotThrow {
                MemberGroupPolicyImpl(buildGroupPolicyNode(mgmInfoOverride = null))
            }
        }

        @Test
        fun `cipher suite must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MemberGroupPolicyImpl(buildGroupPolicyNode(cipherSuiteOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(CIPHER_SUITE))
        }
    }
}