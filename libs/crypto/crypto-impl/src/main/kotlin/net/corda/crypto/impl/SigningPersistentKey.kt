package net.corda.crypto.impl

import java.util.UUID

@Suppress("LongParameterList")
class SigningPersistentKey(
    var sandboxId: String,


    var publicKeyHash: String,

    var externalId: UUID?,

    var publicKey: ByteArray,

    var alias: String?,

    var masterKeyAlias: String?,

    var privateKeyMaterial: ByteArray?,

    var schemeCodeName: String,

    var version: Int = 1
) : Cloneable {
    public override fun clone(): SigningPersistentKey = super.clone() as SigningPersistentKey
}