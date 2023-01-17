package net.corda.membership.lib.impl.grouppolicy.v1

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_2
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.DISTINCT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.impl.grouppolicy.BAD_MGM_GROUP_ID_ERROR
import net.corda.membership.lib.impl.grouppolicy.getBlankValueError
import net.corda.membership.lib.impl.grouppolicy.getMissingKeyError
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class MGMGroupPolicyImplTest {

    companion object {
        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "   "

        private val name = MemberX500Name.parse("O=MGM, L=London, C=GB")
        val holdingIdentity = HoldingIdentity(name, TEST_GROUP_ID)
    }
    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory()
    private val emptyProperties = buildEmptyProperties(layeredPropertyMapFactory)
    private val persistedProperties = buildPersistedProperties(layeredPropertyMapFactory)

    @Nested
    inner class MGMPolicyTests {
        @Test
        fun `complete MGM group policy can be parsed correctly`() {
            val groupPolicy: GroupPolicy = assertDoesNotThrow {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }

            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(TEST_FILE_FORMAT_VERSION)
                it.assertThat(groupPolicy.groupId).isEqualTo(TEST_GROUP_ID)
                it.assertThat(groupPolicy.registrationProtocol).isEqualTo(TEST_REG_PROTOCOL)
                it.assertThat(groupPolicy.synchronisationProtocol).isEqualTo(TEST_SYNC_PROTOCOL)

                /**
                 * The following properties are currently defaults as group policy is not yet persisted at this point.
                 */
                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)
                it.assertThat(groupPolicy.protocolParameters.staticNetworkMembers).isNull()

                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.NO_PKI)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots).isNull()
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots).isEmpty()
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_3)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH_ENCRYPT)
                it.assertThat(groupPolicy.p2pParameters.mgmClientCertificateSubject).isEqualTo(null)

                it.assertThat(groupPolicy.mgmInfo).isNull()
                it.assertThat(groupPolicy.cipherSuite.entries).isEmpty()
            }
        }

        @Test
        fun `complete MGM group policy can be parsed correctly using persisted properties`() {
            val groupPolicy: GroupPolicy = assertDoesNotThrow {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { persistedProperties }
            }

            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(TEST_FILE_FORMAT_VERSION)
                it.assertThat(groupPolicy.groupId).isEqualTo(TEST_GROUP_ID)
                it.assertThat(groupPolicy.registrationProtocol).isEqualTo(TEST_REG_PROTOCOL)
                it.assertThat(groupPolicy.synchronisationProtocol).isEqualTo(TEST_SYNC_PROTOCOL)
                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(DISTINCT)
                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD_EV3)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots).isNotNull
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots?.size).isEqualTo(1)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots?.first()).isEqualTo(TEST_CERT)
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots.size).isEqualTo(1)
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots.first()).isEqualTo(TEST_CERT)
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD_EV3)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_2)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH)
                it.assertThat(groupPolicy.p2pParameters.mgmClientCertificateSubject).isEqualTo(null)

                it.assertThat(groupPolicy.mgmInfo).isNull()
                it.assertThat(groupPolicy.cipherSuite.entries).isEmpty()
            }
        }

        @Test
        fun `persisted properties are not queried until they are needed`() {
            val propertyQuery: () -> LayeredPropertyMap = mock {
                on { invoke() } doReturn persistedProperties
            }
            val groupPolicy: GroupPolicy = assertDoesNotThrow {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    ),
                    propertyQuery
                )
            }
            verify(propertyQuery, never()).invoke()
            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(TEST_FILE_FORMAT_VERSION)
                it.assertThat(groupPolicy.groupId).isEqualTo(TEST_GROUP_ID)
                it.assertThat(groupPolicy.registrationProtocol).isEqualTo(TEST_REG_PROTOCOL)
                it.assertThat(groupPolicy.synchronisationProtocol).isEqualTo(TEST_SYNC_PROTOCOL)
            }
            verify(propertyQuery, never()).invoke()
            assertSoftly {
                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(DISTINCT)
                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD_EV3)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots).isNotNull
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots?.size).isEqualTo(1)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots?.first()).isEqualTo(TEST_CERT)
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots.size).isEqualTo(1)
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots.first()).isEqualTo(TEST_CERT)
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD_EV3)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_2)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH)
                it.assertThat(groupPolicy.p2pParameters.mgmClientCertificateSubject).isEqualTo(null)

                it.assertThat(groupPolicy.mgmInfo).isNull()
                it.assertThat(groupPolicy.cipherSuite.entries).isEmpty()
            }
            verify(propertyQuery).invoke()
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `file format version must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        fileFormatVersionOverride = null,
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(FILE_FORMAT_VERSION))
        }

        @Test
        fun `group policy ID must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = EMPTY_STRING,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(GROUP_ID))
        }

        @Test
        fun `group policy ID must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = WHITESPACE_STRING,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(GROUP_ID))
        }

        @Test
        fun `group policy ID must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = null,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(GROUP_ID))
        }

        @Test
        fun `group policy ID must be the default MGM group ID`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = "aa8cc72a-30e2-49d6-ac01-2fcc3e183200",
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(BAD_MGM_GROUP_ID_ERROR)
        }

        @Test
        fun `registration protocol must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        registrationProtocolOverride = EMPTY_STRING,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `registration protocol must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        registrationProtocolOverride = WHITESPACE_STRING,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `registration protocol must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        registrationProtocolOverride = null,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        syncProtocolOverride = EMPTY_STRING,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SYNC_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        syncProtocolOverride = WHITESPACE_STRING,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SYNC_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                MGMGroupPolicyImpl(
                    holdingIdentity,
                    buildGroupPolicyNode(
                        groupIdOverride = MGM_DEFAULT_GROUP_ID,
                        syncProtocolOverride = null,
                        protocolParametersOverride = null,
                        p2pParametersOverride = null,
                        mgmInfoOverride = null,
                        cipherSuiteOverride = null
                    )
                ) { emptyProperties }
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SYNC_PROTOCOL))
        }
    }
}