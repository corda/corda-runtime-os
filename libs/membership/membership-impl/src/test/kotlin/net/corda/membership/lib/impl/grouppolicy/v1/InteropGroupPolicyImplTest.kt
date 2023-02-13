package net.corda.membership.lib.impl.grouppolicy.v1

import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import org.assertj.core.api.Assertions.entry
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class InteropGroupPolicyImplTest {

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
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        mgmInfoOverride = null
                    )
                )
            }

            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(1)
                it.assertThat(groupPolicy.groupId).isEqualTo("INTEROP")
                it.assertThat(groupPolicy.registrationProtocol)
                    .isEqualTo("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService")
                it.assertThat(groupPolicy.synchronisationProtocol)
                    .isEqualTo("net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService")

                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)

                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.NO_PKI)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots)
                    .isNotNull
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_3)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH_ENCRYPT)

                it.assertThat(groupPolicy.mgmInfo).isNull()

                it.assertThat(groupPolicy.cipherSuite.entries)
                    .isNotEmpty
                    .hasSize(6)
                    .contains(
                        entry("corda.provider", "default"),
                        entry("corda.signature.provider", "default"),
                        entry("corda.signature.default", "ECDSA_SECP256K1_SHA256"),
                        entry("corda.signature.FRESH_KEYS", "ECDSA_SECP256K1_SHA256"),
                        entry("corda.digest.default", "SHA256"),
                        entry("corda.cryptoservice.provider", "default")
                    )
            }
        }

        @Test
        fun `session trust roots can be null if pki mode is NoPKI`() {
            assertDoesNotThrow {
                InteropGroupPolicyImpl(
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
}