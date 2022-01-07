package net.corda.crypto.impl.persistence

import java.util.UUID

@Suppress("LongParameterList")
class SigningPersistentKeyInfo(
    var publicKeyHash: String,
    var alias: String?,
    var hsmAlias: String?,
    var publicKey: ByteArray,
    override var tenantId: String,
    var category: String,
    var externalId: UUID?,
    var masterKeyAlias: String?,
    var privateKeyMaterial: ByteArray?,
    var schemeCodeName: String,
    var version: Int = 1
) : IHaveTenantId, Cloneable {
    public override fun clone(): SigningPersistentKeyInfo = super.clone() as SigningPersistentKeyInfo
}