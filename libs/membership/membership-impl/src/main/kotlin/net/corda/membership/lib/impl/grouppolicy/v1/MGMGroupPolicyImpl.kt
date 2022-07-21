package net.corda.membership.lib.impl.grouppolicy.v1

import com.fasterxml.jackson.databind.JsonNode
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.PropertyKeys
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.grouppolicy.MGMGroupPolicy
import net.corda.membership.lib.impl.grouppolicy.BAD_MGM_GROUP_ID_ERROR
import net.corda.membership.lib.impl.grouppolicy.getMandatoryInt
import net.corda.membership.lib.impl.grouppolicy.getMandatoryString
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.virtualnode.HoldingIdentity

class MGMGroupPolicyImpl(
    holdingIdentity: HoldingIdentity,
    rootNode: JsonNode,
    persistedProperties: LayeredPropertyMap
) : MGMGroupPolicy {
    override val fileFormatVersion = rootNode.getMandatoryInt(FILE_FORMAT_VERSION)

    override val groupId = rootNode.getMandatoryString(GROUP_ID).let {
        if (it != MGM_DEFAULT_GROUP_ID) {
            throw BadGroupPolicyException(BAD_MGM_GROUP_ID_ERROR)
        }
        holdingIdentity.groupId
    }

    override val registrationProtocol =
        persistedProperties.parseOrNull(PropertyKeys.REGISTRATION_PROTOCOL, String::class.java)
            ?: rootNode.getMandatoryString(Root.REGISTRATION_PROTOCOL)

    override val synchronisationProtocol =
        persistedProperties.parseOrNull(PropertyKeys.SYNC_PROTOCOL, String::class.java)
            ?: rootNode.getMandatoryString(SYNC_PROTOCOL)

    override val protocolParameters: GroupPolicy.ProtocolParameters = ProtocolParametersImpl(persistedProperties)

    override val p2pParameters: GroupPolicy.P2PParameters = P2PParametersImpl(persistedProperties)

    override val mgmInfo: GroupPolicy.MGMInfo? = null

    override val cipherSuite: GroupPolicy.CipherSuite = CipherSuiteImpl()

    internal inner class ProtocolParametersImpl(
        persistedProperties: LayeredPropertyMap
    ) : GroupPolicy.ProtocolParameters {
        private val sessionKeyPolicyString = persistedProperties.parseOrNull(PropertyKeys.SESSION_KEY_POLICY, String::class.java)

        override val sessionKeyPolicy = SessionKeyPolicy.fromString(sessionKeyPolicyString) ?: COMBINED
        override val staticNetworkMembers: List<Map<String, Any>>? = null
    }

    internal inner class P2PParametersImpl(
        persistedProperties: LayeredPropertyMap
    ) : GroupPolicy.P2PParameters {
        private val sessionPkiString = persistedProperties.parseOrNull(PropertyKeys.SESSION_PKI, String::class.java)
        private val tlsPkiString = persistedProperties.parseOrNull(PropertyKeys.TLS_PKI, String::class.java)
        private val tlsVersionString = persistedProperties.parseOrNull(PropertyKeys.TLS_VERSION, String::class.java)
        private val protocolModeString = persistedProperties.parseOrNull(PropertyKeys.PROTOCOL_MODE, String::class.java)

        override val sessionPki = SessionPkiMode.fromString(sessionPkiString) ?: NO_PKI
        override val sessionTrustRoots: Collection<String>? =
            if(!persistedProperties.entries.any { it.key.startsWith(PropertyKeys.SESSION_TRUST_ROOTS) }) {
                null
            } else {
                persistedProperties.parseList(PropertyKeys.SESSION_TRUST_ROOTS, String::class.java)
            }
        override val tlsTrustRoots: Collection<String> =
            if(!persistedProperties.entries.any { it.key.startsWith(PropertyKeys.TLS_TRUST_ROOTS) }) {
                emptyList()
            } else {
                persistedProperties.parseList(PropertyKeys.TLS_TRUST_ROOTS, String::class.java)
            }
        override val tlsPki = TlsPkiMode.fromString(tlsPkiString) ?: STANDARD
        override val tlsVersion = TlsVersion.fromString(tlsVersionString) ?: VERSION_1_3
        override val protocolMode = ProtocolMode.fromString(protocolModeString) ?: AUTH_ENCRYPT
    }

    internal inner class CipherSuiteImpl private constructor(
        map: Map<String, String>
    ) : GroupPolicy.CipherSuite, Map<String, String> by map {
        constructor() : this(emptyMap())
    }
}


