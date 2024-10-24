package net.corda.crypto.core

import net.corda.v5.crypto.SecureHash
import java.security.PublicKey
import java.time.Instant

/**
 * The main data transfer object type
 * to and from the [SigningRepository].
 */
@Suppress("LongParameterList")
data class SigningKeyInfo(
    val id: ShortHash,
    val fullId: SecureHash,
    val tenantId: String,
    val category: String,
    val alias: String?,
    val hsmAlias: String?,
    val publicKey: PublicKey,
    val keyMaterial: ByteArray,
    val schemeCodeName: String,
    val wrappingKeyAlias: String,
    val externalId: String?,
    val encodingVersion: Int?,
    val timestamp: Instant,
    val hsmId: String,
    val status: SigningKeyStatus,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SigningKeyInfo

        if (id != other.id) return false
        if (fullId != other.fullId) return false
        if (tenantId != other.tenantId) return false
        if (category != other.category) return false
        if (alias != other.alias) return false
        if (hsmAlias != other.hsmAlias) return false
        if (!publicKey.equals(other.publicKey)) return false
        if (!keyMaterial.contentEquals(other.keyMaterial)) return false
        if (schemeCodeName != other.schemeCodeName) return false
        if (wrappingKeyAlias != other.wrappingKeyAlias) return false
        if (externalId != other.externalId) return false
        if (encodingVersion != other.encodingVersion) return false
        if (timestamp != other.timestamp) return false
        if (hsmId != other.hsmId) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fullId.hashCode()
        result = 31 * result + tenantId.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + (alias?.hashCode() ?: 0)
        result = 31 * result + (hsmAlias?.hashCode() ?: 0)
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + (keyMaterial.contentHashCode())
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + (keyMaterial.contentHashCode())
        result = 31 * result + schemeCodeName.hashCode()
        result = 31 * result + wrappingKeyAlias.hashCode()
        result = 31 * result + (externalId?.hashCode() ?: 0)
        result = 31 * result + (encodingVersion ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + hsmId.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}
