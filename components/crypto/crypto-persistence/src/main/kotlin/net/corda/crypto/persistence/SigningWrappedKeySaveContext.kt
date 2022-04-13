package net.corda.crypto.persistence

import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme

class SigningWrappedKeySaveContext(
    val key: GeneratedWrappedKey,
    val masterKeyAlias: String?,
    override val externalId: String?,
    override val alias: String?,
    override val category: String,
    override val signatureScheme: SignatureScheme
) : SigningKeySaveContext