package net.corda.p2p.linkmanager.grouppolicy

import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants

internal val GroupPolicy.P2PParameters.networkType: NetworkType
    get() {
        return when(this.tlsPki) {
            GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD -> NetworkType.CORDA_5
            GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.CORDA_4 -> NetworkType.CORDA_4
            else -> throw IllegalStateException("Invalid tlsPki value: ${this.tlsPki}")
        }
    }

internal val GroupPolicy.P2PParameters.protocolModes: Set<ProtocolMode>
    get() {
        return when(this.protocolMode) {
            GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT -> setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
            GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH -> setOf(ProtocolMode.AUTHENTICATION_ONLY)
            else -> throw IllegalStateException("Invalid protocol mode: ${this.protocolMode}")
        }
    }
