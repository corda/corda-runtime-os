package net.corda.crypto.persistence

import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme

class SigningPublicKeySaveContext(
    val key: GeneratedPublicKey,
    override val alias: String?,
    override val category: String,
    override val signatureScheme: SignatureScheme
) : SigningKeySaveContext