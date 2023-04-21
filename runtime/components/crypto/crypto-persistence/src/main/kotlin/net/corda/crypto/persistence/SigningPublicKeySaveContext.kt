package net.corda.crypto.persistence

import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme

@Suppress("LongParameterList")
class SigningPublicKeySaveContext(
    val key: GeneratedPublicKey,
    override val alias: String?,
    override val category: String,
    override val keyScheme: KeyScheme,
    override val externalId: String?,
    override val hsmId: String
) : SigningKeySaveContext