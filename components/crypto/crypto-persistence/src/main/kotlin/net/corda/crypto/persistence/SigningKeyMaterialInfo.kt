package net.corda.crypto.persistence

import java.util.UUID

class SigningKeyMaterialInfo(
    val signingKeyId: UUID,
    val keyMaterial: ByteArray
) {
    // Custom equals and hashcode due to ByteArray.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SigningKeyMaterialInfo

        if (signingKeyId != other.signingKeyId) return false
        if (!keyMaterial.contentEquals(other.keyMaterial)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signingKeyId.hashCode()
        result = 31 * result + keyMaterial.contentHashCode()
        return result
    }
}
