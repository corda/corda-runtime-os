package net.corda.crypto.service.persistence

class WrappingKeyInfo(
    val encodingVersion: Int,
    val alias: String,
    val algorithmName: String,
    val keyMaterial: ByteArray
)