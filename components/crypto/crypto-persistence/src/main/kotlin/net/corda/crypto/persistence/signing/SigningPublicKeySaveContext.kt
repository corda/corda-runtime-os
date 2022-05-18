package net.corda.crypto.persistence.signing

import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme

@Suppress("LongParameterList")
class SigningPublicKeySaveContext(
    val key: GeneratedPublicKey,
    override val alias: String?,
    override val category: String,
    override val signatureScheme: SignatureScheme,
    override val externalId: String?,
    override val associationId: String
) : SigningKeySaveContext