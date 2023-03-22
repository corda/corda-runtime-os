package net.corda.crypto.persistence

/**
 * A wrapping key and metadata for unwrapping it
 *
 * @param encodingVersion good question.  It the version linked to the algorithm or how many times the key was wrapped? Or...?
 * @param algorithmName the algorigthm used to encode the key (eg. SHA-256)
 * @param keyMaterial the actual encoded key
 */
class WrappingKeyInfo(
    val encodingVersion: Int,
    val algorithmName: String,
    val keyMaterial: ByteArray
)