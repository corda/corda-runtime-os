package net.corda.crypto.cipher.suite

/**
 * Parameters for the wrapped key.
 *
 * @property keyMaterial The encoded and encrypted private key.
 * @property wrappingKeyAlias The wrapping key's alias which was used for wrapping, the value
 *           could still be null for HSMs which use built-in wrapping keys.
 * @property encodingVersion The encoding version which was used to encode the private key.
 */
class KeyMaterialSpec(
    val keyMaterial: ByteArray,
    val wrappingKeyAlias: String,
    val encodingVersion: Int
) {
    override fun toString(): String =
        "$wrappingKeyAlias=$wrappingKeyAlias,encVer=$encodingVersion"
}