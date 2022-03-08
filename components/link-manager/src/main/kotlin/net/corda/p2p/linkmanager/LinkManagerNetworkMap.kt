package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

typealias PemCertificates = String

/**
 * This interface defines the parts of the Network Map required by the LinkManager.
 */
interface LinkManagerNetworkMap: LifecycleWithDominoTile {

    companion object {
        internal fun net.corda.data.identity.HoldingIdentity.toHoldingIdentity(): HoldingIdentity {
            return HoldingIdentity(x500Name, groupId)
        }

        internal fun NetworkType.toNetworkType(): net.corda.p2p.NetworkType {
            return when (this) {
                NetworkType.CORDA_4 -> net.corda.p2p.NetworkType.CORDA_4
                NetworkType.CORDA_5 -> net.corda.p2p.NetworkType.CORDA_5
            }
        }
    }

    /**
     * Returns the [MemberInfo] belonging a specific [holdingIdentity].
     */
    fun getMemberInfo(holdingIdentity: HoldingIdentity): MemberInfo?

    /**
     * Returns the [MemberInfo] which has a public key with SHA-256 hash [hash] and group identifier [groupId].
     */
    fun getMemberInfo(hash: ByteArray, groupId: String): MemberInfo?

    /**
     * Returns the [NetworkType] for group identifier [groupId].
     */
    fun getNetworkType(groupId: String): NetworkType?

    /**
     * Register a listener to changes in the network map
     */
    fun registerListener(networkMapListener: NetworkMapListener)

    data class MemberInfo(val holdingIdentity: HoldingIdentity,
                          val publicKey: PublicKey,
                          val publicKeyAlgorithm: KeyAlgorithm,
                          val endPoint: EndPoint,
    ) {

        fun getSignatureSpec(): SignatureSpec {
            return when (publicKeyAlgorithm) {
                KeyAlgorithm.RSA -> RSA_SHA256_SIGNATURE_SPEC
                KeyAlgorithm.ECDSA -> ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
            }
        }
    }

    data class EndPoint(val address: String)

    data class HoldingIdentity(val x500Name: String, val groupId: String) {
        fun toHoldingIdentity(): net.corda.data.identity.HoldingIdentity {
            return net.corda.data.identity.HoldingIdentity(x500Name, groupId)
        }
    }

    enum class NetworkType {
        CORDA_4, CORDA_5
    }
}
