package net.corda.crypto.impl.persistence

import java.util.UUID

@Suppress("LongParameterList")
class SigningPersistentKeyInfo(
    var publicKeyHash: String,
    var alias: String?,
    var publicKey: ByteArray,
    var memberId: String,
    var externalId: UUID?,
    var masterKeyAlias: String?,
    var privateKeyMaterial: ByteArray?,
    var schemeCodeName: String,
    var version: Int = 1
) : Cloneable {
    public override fun clone(): SigningPersistentKeyInfo = super.clone() as SigningPersistentKeyInfo
}