package net.corda.membership.lib.impl.grouppolicy.v1

import com.fasterxml.jackson.databind.JsonNode
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.MGM_CLIENT_CERTIFICATE_SUBJECT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TYPE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy
import net.corda.membership.lib.grouppolicy.InteropGroupPolicy
import net.corda.membership.lib.impl.grouppolicy.getMandatoryEnum
import net.corda.membership.lib.impl.grouppolicy.getMandatoryInt
import net.corda.membership.lib.impl.grouppolicy.getMandatoryJsonNode
import net.corda.membership.lib.impl.grouppolicy.getMandatoryString
import net.corda.membership.lib.impl.grouppolicy.getMandatoryStringList
import net.corda.membership.lib.impl.grouppolicy.getMandatoryStringMap
import net.corda.membership.lib.impl.grouppolicy.getMissingCertError
import net.corda.membership.lib.impl.grouppolicy.getOptionalString
import net.corda.membership.lib.impl.grouppolicy.getOptionalStringList
import net.corda.membership.lib.impl.grouppolicy.getOptionalStringMap
import net.corda.membership.lib.impl.grouppolicy.validatePemCert
import net.corda.v5.base.types.MemberX500Name

class InteropGroupPolicyImpl(rootNode: JsonNode) : InteropGroupPolicy {

    override val fileFormatVersion = rootNode.getMandatoryInt(FILE_FORMAT_VERSION)

    override val groupId = rootNode.getMandatoryString(GROUP_ID)

    // registrationProtocol is not required in interop group policy so not fetching from root node and set to empty string
    override val registrationProtocol = ""

    // synchronisationProtocol is not required in interop group policy so not fetching from root node and set to empty string
    override val synchronisationProtocol = ""

    // protocolParameters are not required in interop group policy so not fetching from root node and set to some default
    override val protocolParameters: GroupPolicy.ProtocolParameters = ProtocolParametersImpl()

    override val p2pParameters: GroupPolicy.P2PParameters = P2PParametersImpl(rootNode)

    // mgmInfo is not required in interop group policy so set to some default
    override val mgmInfo: GroupPolicy.MGMInfo? = rootNode.getOptionalStringMap(MGM_INFO)?.let {
        MGMInfoImpl(it)
    }

    override val cipherSuite: GroupPolicy.CipherSuite = CipherSuiteImpl(emptyMap())

    internal inner class ProtocolParametersImpl : GroupPolicy.ProtocolParameters {
        override val sessionKeyPolicy = SessionKeyPolicy.COMBINED
        override val staticNetworkMembers: List<Map<String, Any>>? = emptyList()
        override val staticNetworkGroupParameters: Map<String, String>?
            get() = emptyMap()
    }

    internal inner class P2PParametersImpl(rootNode: JsonNode) : GroupPolicy.P2PParameters {
        private val p2pParameters = rootNode.getMandatoryJsonNode(P2P_PARAMETERS)

        override val sessionPki = p2pParameters.getMandatoryEnum(SESSION_PKI) {
            when (it.lowercase()) {
                SessionPkiMode.STANDARD.toString().lowercase() -> SessionPkiMode.STANDARD
                SessionPkiMode.STANDARD_EV3.toString().lowercase() -> SessionPkiMode.STANDARD_EV3
                SessionPkiMode.CORDA_4.toString().lowercase() -> SessionPkiMode.CORDA_4
                SessionPkiMode.NO_PKI.toString().lowercase() -> SessionPkiMode.NO_PKI
                else -> throw IllegalArgumentException(
                    "\"$it\" is not a valid session pki mode."
                            + "Allowed values are: [${SessionPkiMode.values().joinToString()}]"
                )
            }
        }

        override val sessionTrustRoots: Collection<String>? =
            if (sessionPki == SessionPkiMode.NO_PKI) {
                p2pParameters.getOptionalStringList(SESSION_TRUST_ROOTS)
            } else {
                p2pParameters.getMandatoryStringList(SESSION_TRUST_ROOTS).apply {
                    if (isEmpty()) {
                        throw BadGroupPolicyException(getMissingCertError(SESSION_TRUST_ROOTS))
                    }
                }
            }?.onEachIndexed { index, pemCert -> validatePemCert(pemCert, SESSION_TRUST_ROOTS, index) }


        override val tlsTrustRoots: Collection<String> =
            p2pParameters.getMandatoryStringList(TLS_TRUST_ROOTS).apply {
                if (isEmpty()) {
                    throw BadGroupPolicyException(getMissingCertError(TLS_TRUST_ROOTS))
                }
            }.onEachIndexed { index, pemCert -> validatePemCert(pemCert, TLS_TRUST_ROOTS, index) }


        override val tlsPki = p2pParameters.getMandatoryEnum(TLS_PKI) {
            when (it.lowercase()) {
                TlsPkiMode.STANDARD.toString().lowercase() -> TlsPkiMode.STANDARD
                TlsPkiMode.STANDARD_EV3.toString().lowercase() -> TlsPkiMode.STANDARD_EV3
                TlsPkiMode.CORDA_4.toString().lowercase() -> TlsPkiMode.CORDA_4
                else -> throw IllegalArgumentException(
                    "\"$it\" is not a valid tls pki mode."
                            + "Allowed values are: [${TlsPkiMode.values().joinToString()}]"
                )
            }
        }

        override val tlsVersion = p2pParameters.getMandatoryEnum(TLS_VERSION) {
            when (it.lowercase()) {
                TlsVersion.VERSION_1_2.toString().lowercase() -> TlsVersion.VERSION_1_2
                TlsVersion.VERSION_1_3.toString().lowercase() -> TlsVersion.VERSION_1_3
                else -> throw IllegalArgumentException(
                    "\"$it\" is not a valid tls version. "
                            + "Allowed values are: [${TlsVersion.values().joinToString()}]"
                )
            }
        }

        override val mgmClientCertificateSubject: MemberX500Name?
            get() = p2pParameters.getOptionalString(MGM_CLIENT_CERTIFICATE_SUBJECT)?.let {
                MemberX500Name.parse(it)
            }

        override val protocolMode = p2pParameters.getMandatoryEnum(PROTOCOL_MODE) {
            when (it.lowercase()) {
                ProtocolMode.AUTH.toString().lowercase() -> ProtocolMode.AUTH
                ProtocolMode.AUTH_ENCRYPT.toString().lowercase() -> ProtocolMode.AUTH_ENCRYPT
                else -> throw IllegalArgumentException(
                    "\"$it\" is not a valid protocol mode. "
                            + "Allowed values are: [${ProtocolMode.values().joinToString()}]"
                )
            }
        }
        override val tlsType = p2pParameters.getMandatoryEnum(TLS_TYPE) { name ->
            TlsType.values().firstOrNull { type ->
                type.groupPolicyName.equals(name, ignoreCase = true)
            } ?: throw IllegalArgumentException(
                "\"$name\" is not a valid TLS type. "
                        + "Allowed values are: [${TlsType.values().map { it.groupPolicyName }}]"
            )
        }
    }

    internal inner class MGMInfoImpl(
        map: Map<String, String>
    ) : GroupPolicy.MGMInfo, Map<String, String> by map

    internal inner class CipherSuiteImpl(
        map: Map<String, String>
    ) : GroupPolicy.CipherSuite, Map<String, String> by map
}