package net.corda.membership.lib.impl.grouppolicy.v1

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TYPE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.STATIC_NETWORK
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.StaticNetwork.MEMBERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType.ONE_WAY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED

val R3_COM_CERT = ClassLoader.getSystemResource("r3Com.pem")
    .readText()
    .replace("\r", "")
    .replace("\n", System.lineSeparator())

val UNPARSEABLE_CERT = ClassLoader.getSystemResource("invalidCert.pem")
    .readText()
    .replace("\r", "")
    .replace("\n", System.lineSeparator())

const val TEST_FILE_FORMAT_VERSION = 1
const val TEST_GROUP_ID = "13822f7f-0d2c-450b-8f6f-93c3b8ce9602"
const val TEST_REG_PROTOCOL = "com.foo.bar.RegistrationProtocol"
const val TEST_SYNC_PROTOCOL = "com.foo.bar.SyncProtocol"
const val TEST_CERT = "-----BEGIN CERTIFICATE-----Base64–encoded certificate-----END CERTIFICATE-----"

const val TEST_STATIC_MEMBER_KEY = "foo"
const val TEST_STATIC_MEMBER_VALUE = "bar"

const val TEST_MGM_INFO_STRING_KEY = "foo-str"
const val TEST_MGM_INFO_INT_KEY = "foo-int"
const val TEST_MGM_INFO_STRING_VAL = "bar"
const val TEST_MGM_INFO_INT_VAL = 1

const val TEST_CIPHER_SUITE_KEY = "foo"
const val TEST_CIPHER_SUITE_VAL = "bar"

fun buildStaticMemberTemplate(
    members: List<Map<String, String>>? = listOf(
        mapOf(TEST_STATIC_MEMBER_KEY to TEST_STATIC_MEMBER_VALUE),
        mapOf(TEST_STATIC_MEMBER_KEY to TEST_STATIC_MEMBER_VALUE)
    )
) = mutableMapOf<String, Any>().apply {
    members?.let { put(MEMBERS, members) }
}

fun buildProtocolParameters(
    sessionKeyPolicyOverride: String? = COMBINED.toString(),
    staticNetworkOverride: Map<String, Any>? = buildStaticMemberTemplate()
) = mutableMapOf<String, Any>().apply {
    sessionKeyPolicyOverride?.let { put(SESSION_KEY_POLICY, it) }
    staticNetworkOverride?.let { put(STATIC_NETWORK, it) }
}

@Suppress("LongParameterList")
fun buildP2PParameters(
    sessionPkiOverride: String? = SessionPkiMode.STANDARD.toString(),
    sessionTrustRootOverride: List<String>? = listOf(R3_COM_CERT),
    tlsTrustRootOverride: List<String>? = listOf(R3_COM_CERT),
    tlsPkiOverride: String? = TlsPkiMode.STANDARD.toString(),
    tlsVersionOverride: String? = VERSION_1_3.toString(),
    protocolModeOverride: String? = AUTH_ENCRYPT.toString(),
) = mutableMapOf<String, Any>().apply {
    sessionPkiOverride?.let { put(SESSION_PKI, it) }
    sessionTrustRootOverride?.let { put(SESSION_TRUST_ROOTS, it) }
    tlsTrustRootOverride?.let { put(TLS_TRUST_ROOTS, it) }
    tlsPkiOverride?.let { put(TLS_PKI, it) }
    tlsVersionOverride?.let { put(TLS_VERSION, it) }
    protocolModeOverride?.let { put(PROTOCOL_MODE, it) }
    put(TLS_TYPE, ONE_WAY.groupPolicyName)
}

@Suppress("LongParameterList", "ComplexMethod")
fun buildGroupPolicyNode(
    fileFormatVersionOverride: Int? = TEST_FILE_FORMAT_VERSION,
    groupIdOverride: String? = TEST_GROUP_ID,
    registrationProtocolOverride: String? = TEST_REG_PROTOCOL,
    syncProtocolOverride: String? = TEST_SYNC_PROTOCOL,
    protocolParametersOverride: Map<String, Any>? = buildProtocolParameters(),
    p2pParametersOverride: Map<String, Any>? = buildP2PParameters(),
    mgmInfoOverride: Map<String, Any>? = mapOf(
        TEST_MGM_INFO_STRING_KEY to TEST_MGM_INFO_STRING_VAL,
        TEST_MGM_INFO_INT_KEY to TEST_MGM_INFO_INT_VAL
    ),
    cipherSuiteOverride: Map<String, String>? = mapOf(TEST_CIPHER_SUITE_KEY to TEST_CIPHER_SUITE_VAL),
): JsonNode = ObjectMapper().let { objMapper ->
    objMapper.readTree(
        objMapper.writeValueAsString(
            mutableMapOf<String, Any>().apply {
                fileFormatVersionOverride?.let { put(FILE_FORMAT_VERSION, it) }
                groupIdOverride?.let { put(GROUP_ID, it) }
                registrationProtocolOverride?.let { put(REGISTRATION_PROTOCOL, it) }
                syncProtocolOverride?.let { put(SYNC_PROTOCOL, it) }
                protocolParametersOverride?.let { put(PROTOCOL_PARAMETERS, it) }
                p2pParametersOverride?.let { put(P2P_PARAMETERS, it) }
                mgmInfoOverride?.let { put(MGM_INFO, it) }
                cipherSuiteOverride?.let { put(CIPHER_SUITE, it) }
            }
        )
    )
}

fun buildEmptyProperties(layeredPropertyMapFactory: LayeredPropertyMapFactory) = layeredPropertyMapFactory.createMap(emptyMap())

fun buildPersistedProperties(layeredPropertyMapFactory: LayeredPropertyMapFactory) =
    layeredPropertyMapFactory.createMap(
        mapOf(
            "protocol.p2p.mode" to "Authentication",
            "key.session.policy" to "Distinct",
            "pki.session" to "StandardEV3",
            "pki.tls" to "StandardEV3",
            "truststore.session.0" to TEST_CERT,
            "truststore.tls.0" to TEST_CERT,
            "tls.version" to "1.2",
        )
    )