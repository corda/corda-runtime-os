package net.corda.membership.certificates

import net.corda.data.certificates.CertificateType
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.virtualnode.ShortHash

/**
 * Either a certificate type (to indicate a cluster level certificate) or a holding identity ID (to indicate
 * a virtual node certificate)
 */
sealed interface CertificateUsage {
    companion object {
        val CertificateType.publicName: String
            get() = name.lowercase().replace("_", "-")

        fun fromString(str: String): CertificateUsage {
            return Type.fromString(str) ?: HoldingIdentityId.fromString(str)
        }

        fun fromAvro(request: CertificateRpcRequest): CertificateUsage? {
            return request.usage.let { usage ->
                when (usage) {
                    is CertificateType -> usage.fromAvro
                    is String -> HoldingIdentityId.fromString(usage)
                    else -> null
                }
            }
        }

        val CertificateType.fromAvro: CertificateUsage
            get() = Type(this)
    }

    val asAvro: Any

    /**
     * Cluster level certificate type
     */
    data class Type(override val asAvro: CertificateType) : CertificateUsage {

        companion object {
            internal fun fromString(string: String): CertificateUsage? {
                return CertificateType.values().firstOrNull {
                    it.name.equals(string, ignoreCase = true) || it.publicName.equals(string, ignoreCase = true)
                }?.fromAvro
            }
        }
    }

    /**
     * Virtual node certificate
     */
    data class HoldingIdentityId(
        /**
         * The virtual node holding identity ID
         */
        val holdingIdentityId: ShortHash
    ) : CertificateUsage {
        companion object {
            internal fun fromString(string: String): HoldingIdentityId {
                return HoldingIdentityId(ShortHash.of(string))
            }
        }

        override val asAvro: String
            get() = holdingIdentityId.value
    }
}
