package net.corda.crypto.impl.persistence

@Suppress("LongParameterList")
class DefaultCryptoPersistentKeyInfo(
    override var memberId: String,
    var alias: String,
    var publicKey: ByteArray? = null,
    var privateKey: ByteArray = ByteArray(0),
    var algorithmName: String,
    var version: Int = 1
) : IHaveMemberId