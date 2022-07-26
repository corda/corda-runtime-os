package net.corda.crypto.persistence

class WrappingKeyInfo(
    val encodingVersion: Int,
    val algorithmName: String,
    val keyMaterial: ByteArray
)