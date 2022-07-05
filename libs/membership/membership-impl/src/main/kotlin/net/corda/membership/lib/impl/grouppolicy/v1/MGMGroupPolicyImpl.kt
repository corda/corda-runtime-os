package net.corda.membership.lib.impl.grouppolicy.v1

import com.fasterxml.jackson.databind.JsonNode
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.impl.grouppolicy.BAD_MGM_GROUP_ID_ERROR
import net.corda.membership.lib.impl.grouppolicy.getMandatoryInt
import net.corda.membership.lib.impl.grouppolicy.getMandatoryString
import net.corda.virtualnode.HoldingIdentity

class MGMGroupPolicyImpl(
    holdingIdentity: HoldingIdentity,
    rootNode: JsonNode
) : GroupPolicy {
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
        override val sessionKeyPolicy = COMBINED
        override val staticNetworkMembers: List<Map<String, Any>>? = null
    }

    internal inner class P2PParametersImpl : GroupPolicy.P2PParameters {
        override val sessionPki = NO_PKI
        override val sessionTrustRoots: Collection<String>? = null
        override val tlsTrustRoots: Collection<String> = emptyList()
        override val tlsPki = STANDARD
        override val tlsVersion = VERSION_1_3
        override val protocolMode = AUTH_ENCRYPT
    }

    internal inner class CipherSuiteImpl private constructor(
        map: Map<String, String>
    ) : GroupPolicy.CipherSuite, Map<String, String> by map {
        constructor() : this(emptyMap())
    }
}


