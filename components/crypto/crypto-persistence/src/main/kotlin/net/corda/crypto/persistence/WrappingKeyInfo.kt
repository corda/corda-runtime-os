package net.corda.crypto.persistence

/**
 * A wrapping key and metadata for unwrapping it
 *
 * @param encodingVersion good question.  It the version linked to the algorithm or how many times the key was wrapped? Or...?
 * @param algorithmName the algorithm used to encode the key (eg. SHA-256)
 * @param keyMaterial the actual encoded key
 * @param generation
 * @param parentKeyAlias
 * @param alias the alias of the wrapping key
 */
data class WrappingKeyInfo(
    val encodingVersion: Int,
    val algorithmName: String,
    val keyMaterial: ByteArray,
    val generation: Int,
    val parentKeyAlias: String,
    val alias: String,
) {
    // Custom equals and hashcode due to ByteArray.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappingKeyInfo

        if (encodingVersion != other.encodingVersion) return false
        if (algorithmName != other.algorithmName) return false
        if (!keyMaterial.contentEquals(other.keyMaterial)) return false
        if (generation != other.generation) return false
        if (parentKeyAlias != other.parentKeyAlias) return false
        if (alias != other.alias) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encodingVersion
        result = 31 * result + algorithmName.hashCode()
        result = 31 * result + keyMaterial.contentHashCode()
        result = 31 * result + generation
        result = 31 * result + parentKeyAlias.hashCode()
        result = 31 * result + alias.hashCode()
        return result
    }
}
