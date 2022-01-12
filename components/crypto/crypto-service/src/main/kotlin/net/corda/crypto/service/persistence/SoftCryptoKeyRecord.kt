package net.corda.crypto.service.persistence

@Suppress("LongParameterList")
class SoftCryptoKeyRecord(
    var tenantId: String,
    var alias: String,
    var publicKey: ByteArray? = null,
    var privateKey: ByteArray = ByteArray(0),
    var algorithmName: String,
    var version: Int = 1
)