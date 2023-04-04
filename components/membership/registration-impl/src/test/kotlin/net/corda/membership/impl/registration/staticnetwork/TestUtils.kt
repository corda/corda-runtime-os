package net.corda.membership.impl.registration.staticnetwork

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.NAME
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TYPE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.STATIC_NETWORK
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.StaticNetwork.MEMBERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType.ONE_WAY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.DISTINCT
import net.corda.membership.lib.impl.grouppolicy.v1.MemberGroupPolicyImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import org.apache.commons.text.StringEscapeUtils
import org.mockito.kotlin.mock
import java.util.UUID

class TestUtils {
    companion object {
        val DUMMY_GROUP_ID = UUID(0, 1).toString()

        private const val TEST_ENDPOINT_PROTOCOL = "1"
        private const val TEST_ENDPOINT_URL = "https://dummyurl.corda5.r3.com:10000"

        private val messagingConfig: SmartConfig = mock()
        private val bootConfig: SmartConfig = mock {
            on(it.withFallback(messagingConfig)).thenReturn(messagingConfig)
        }

        val configs = mapOf(
            ConfigKeys.BOOT_CONFIG to bootConfig,
            ConfigKeys.MESSAGING_CONFIG to messagingConfig
        )

        val aliceName = MemberX500Name("Alice", "London", "GB")
        val bobName = MemberX500Name("Bob", "London", "GB")
        val charlieName = MemberX500Name("Charlie", "London", "GB")
        val daisyName = MemberX500Name("Daisy", "London", "GB")
        val ericName = MemberX500Name("Eric", "London", "GB")


        val r3comCert = StringEscapeUtils.escapeJson(
            TestUtils::class.java.getResource("/r3Com.pem")!!.readText()
            .replace("\r", "")
            .replace("\n", System.lineSeparator())
        )

        private val staticMemberTemplate = """
            [
                {
                    "$NAME": "$aliceName",
                    "$MEMBER_STATUS": "$MEMBER_STATUS_ACTIVE",
                    "${String.format(ENDPOINT_URL, 1)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 1)}": "$TEST_ENDPOINT_PROTOCOL"
                },
                {
                    "$NAME": "$bobName",
                    "$MEMBER_STATUS": "$MEMBER_STATUS_ACTIVE",
                    "${String.format(ENDPOINT_URL, 1)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 1)}": "$TEST_ENDPOINT_PROTOCOL"
                },
                {
                    "$NAME": "$charlieName",
                    "$MEMBER_STATUS": "$MEMBER_STATUS_SUSPENDED",
                    "${String.format(ENDPOINT_URL, 1)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 1)}": "$TEST_ENDPOINT_PROTOCOL",
                    "${String.format(ENDPOINT_URL, 2)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 2)}": "$TEST_ENDPOINT_PROTOCOL"   
                }
            ]
        """.trimIndent()

        private val staticMemberTemplateWithDuplicatedVNodeName = """
            [
                {
                    "$NAME": "$aliceName",
                    "$MEMBER_STATUS": "$MEMBER_STATUS_ACTIVE",
                    "${String.format(ENDPOINT_URL, 1)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 1)}": "$TEST_ENDPOINT_PROTOCOL"
                },
                {
                    "$NAME": "$aliceName",
                    "$MEMBER_STATUS": "$MEMBER_STATUS_ACTIVE",
                    "${String.format(ENDPOINT_URL, 1)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 1)}": "$TEST_ENDPOINT_PROTOCOL"
                },
                {
                    "$NAME": "$charlieName",
                    "$MEMBER_STATUS": "$MEMBER_STATUS_SUSPENDED",
                    "${String.format(ENDPOINT_URL, 1)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 1)}": "$TEST_ENDPOINT_PROTOCOL",
                    "${String.format(ENDPOINT_URL, 2)}": "$TEST_ENDPOINT_URL",
                    "${String.format(ENDPOINT_PROTOCOL, 2)}": "$TEST_ENDPOINT_PROTOCOL"   
                }
            ]
        """.trimIndent()


        val groupPolicyWithStaticNetwork = MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegistrationProtocol",
                    "$SYNC_PROTOCOL": "com.foo.bar.SyncProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$COMBINED",
                        "$STATIC_NETWORK": {
                            "$MEMBERS": $staticMemberTemplate
                        }
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                            "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )

        val groupPolicyWithStaticNetworkAndDuplicatedVNodeName = MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegistrationProtocol",
                    "$SYNC_PROTOCOL": "com.foo.bar.SyncProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$COMBINED",
                        "$STATIC_NETWORK": {
                            "$MEMBERS": $staticMemberTemplateWithDuplicatedVNodeName
                        }
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                            "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )

        val groupPolicyWithStaticNetworkAndDistinctKeys = MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegistrationProtocol",
                    "$SYNC_PROTOCOL": "com.foo.bar.SyncProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$DISTINCT",
                        "$STATIC_NETWORK": {
                            "$MEMBERS": $staticMemberTemplate
                        }
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                            "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )

        val groupPolicyWithInvalidStaticNetworkTemplate = MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegistrationProtocol",
                    "$SYNC_PROTOCOL": "com.foo.bar.SyncProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$COMBINED",
                        "$STATIC_NETWORK": {
                            "$MEMBERS": [
                                {
                                    "key": "value"
                                }
                            ]
                        }
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                            "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )

        val groupPolicyWithoutStaticNetwork = MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegistrationProtocol",
                    "$SYNC_PROTOCOL": "com.foo.bar.SyncProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$COMBINED"
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                            "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )

        val groupPolicyWithEmptyStaticNetwork = MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegistrationProtocol",
                    "$SYNC_PROTOCOL": "com.foo.bar.SyncProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$COMBINED",
                        "$STATIC_NETWORK": {
                            "$MEMBERS": []
                        }
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                            "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )
    }
}