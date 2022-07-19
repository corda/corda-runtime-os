package net.corda.crypto.persistence.wrapping

class WrappingKeyInfo(
    val encodingVersion: Int,
    val algorithmName: String,
    val keyMaterial: ByteArray
)