package net.corda.p2p.linkmanager

import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import java.security.PublicKey

typealias PemCertificates = String

object LinkManagerInternalTypes {

    internal fun net.corda.data.identity.HoldingIdentity.toHoldingIdentity(): HoldingIdentity {
        return HoldingIdentity(x500Name, groupId)
    }

    internal fun NetworkType.toNetworkType(): net.corda.p2p.NetworkType {
        return when (this) {
            NetworkType.CORDA_4 -> net.corda.p2p.NetworkType.CORDA_4
            NetworkType.CORDA_5 -> net.corda.p2p.NetworkType.CORDA_5
        }
    }

    data class MemberInfo(
        val holdingIdentity: HoldingIdentity,
        val publicKey: PublicKey,
        val publicKeyAlgorithm: KeyAlgorithm,
        val endPoint: EndPoint,
    )

    data class EndPoint(val address: String)

    data class HoldingIdentity(val x500Name: String, val groupId: String) {
        fun toHoldingIdentity(): net.corda.data.identity.HoldingIdentity {
            return net.corda.data.identity.HoldingIdentity(x500Name, groupId)
        }
    }

    enum class NetworkType {
        CORDA_4, CORDA_5
    }

    fun net.corda.p2p.NetworkType.toLMNetworkType(): NetworkType {
        return when (this) {
            net.corda.p2p.NetworkType.CORDA_4 -> NetworkType.CORDA_4
            net.corda.p2p.NetworkType.CORDA_5 -> NetworkType.CORDA_5
        }
    }
}
