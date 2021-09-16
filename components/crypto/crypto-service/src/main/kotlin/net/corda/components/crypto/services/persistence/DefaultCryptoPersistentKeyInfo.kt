package net.corda.components.crypto.services.persistence

@Suppress("LongParameterList")
class DefaultCryptoPersistentKeyInfo(
    var memberId: String,
    var alias: String,
    var publicKey: ByteArray? = null,
    var privateKey: ByteArray = ByteArray(0),
    var algorithmName: String,
    var version: Int = 1
)