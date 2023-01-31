package net.corda.crypto.service

val WRAPPING_KEY_ENCODING_VERSION: Int = 1
val PRIVATE_KEY_ENCODING_VERSION: Int = 1

class PrivateKeyMaterial(
    val encodingVersion: Int,
    val keyMaterial: ByteArray
)