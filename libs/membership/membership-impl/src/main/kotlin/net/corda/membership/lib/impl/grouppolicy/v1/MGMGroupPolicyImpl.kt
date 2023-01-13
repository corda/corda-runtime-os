package net.corda.membership.lib.impl.grouppolicy.v1

import com.fasterxml.jackson.databind.JsonNode
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PropertyKeys
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
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
    groupPolicyPropertiesQuery: () -> LayeredPropertyMap?
) : MGMGroupPolicy {

    /**
     * Properties are persisted when the MGM is onboarded but the group policy needs to be accessible before that.
     * e.g. to select the registration protocol. Lazy initialisation on persisted properties is to hold off on setting
     * values until a later time when the MGM has fully onboarded.
     */
    private val persistedProperties by lazy {
        groupPolicyPropertiesQuery.invoke()
            ?: throw BadGroupPolicyException("Could not query for group policy parameters.")
    }

    private fun getPersistedString(key: String): String? {
        return persistedProperties.parseOrNull(
            key,
            String::class.java
        )
    }

    override val fileFormatVersion = rootNode.getMandatoryInt(FILE_FORMAT_VERSION)

    override val groupId = rootNode.getMandatoryString(GROUP_ID).let {
        if (it != MGM_DEFAULT_GROUP_ID) {
            throw BadGroupPolicyException(BAD_MGM_GROUP_ID_ERROR)
        }
        holdingIdentity.groupId
    }

    override val registrationProtocol = rootNode.getMandatoryString(REGISTRATION_PROTOCOL)

    override val synchronisationProtocol = rootNode.getMandatoryString(SYNC_PROTOCOL)

    override val protocolParameters: GroupPolicy.ProtocolParameters = ProtocolParametersImpl()

    override val p2pParameters: GroupPolicy.P2PParameters = P2PParametersImpl()

    override val mgmInfo: GroupPolicy.MGMInfo? = null

    override val cipherSuite: GroupPolicy.CipherSuite = CipherSuiteImpl()

    internal inner class ProtocolParametersImpl : GroupPolicy.ProtocolParameters {
        override val sessionKeyPolicy by lazy {
            SessionKeyPolicy.fromString(
                getPersistedString(
                    PropertyKeys.SESSION_KEY_POLICY
                )
            ) ?: COMBINED
        }

        override val staticNetworkMembers: List<Map<String, Any>>? = null
    }

    internal inner class P2PParametersImpl : GroupPolicy.P2PParameters {

        override val sessionPki by lazy {
            SessionPkiMode.fromString(
                getPersistedString(
                    PropertyKeys.SESSION_PKI_MODE
                )
            ) ?: NO_PKI
        }

        override val sessionTrustRoots by lazy {
            if (!persistedProperties.entries.any { it.key.startsWith(PropertyKeys.SESSION_TRUST_ROOTS) }) {
                null
            } else {
                persistedProperties.parseList(PropertyKeys.SESSION_TRUST_ROOTS, String::class.java)
            }
        }

        override val tlsTrustRoots by lazy {
            if (!persistedProperties.entries.any { it.key.startsWith(PropertyKeys.TLS_TRUST_ROOTS) }) {
                emptyList()
            } else {
                persistedProperties.parseList(PropertyKeys.TLS_TRUST_ROOTS, String::class.java)
            }
        }

        override val tlsPki by lazy {
            TlsPkiMode.fromString(
                getPersistedString(PropertyKeys.TLS_PKI_MODE)
            ) ?: STANDARD
        }
        override val tlsVersion by lazy {
            TlsVersion.fromString(
                getPersistedString(PropertyKeys.TLS_VERSION)
            ) ?: VERSION_1_3
        }
        override val protocolMode by lazy {
            ProtocolMode.fromString(
                getPersistedString(PropertyKeys.P2P_PROTOCOL_MODE)
            ) ?: AUTH_ENCRYPT
        }
        override val tlsType by lazy {
            TlsType.fromString(
                getPersistedString(PropertyKeys.TLS_TYPE)
            ) ?: TlsType.ONE_WAY
        }
    }

    internal inner class CipherSuiteImpl private constructor(
        map: Map<String, String>
    ) : GroupPolicy.CipherSuite, Map<String, String> by map {
        constructor() : this(emptyMap())
    }
}


