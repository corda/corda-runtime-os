package net.corda.crypto.service.persistence

import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.cipher.suite.handlers.generation.GeneratedPublicKey

class SigningPublicKeySaveContext(
    val key: GeneratedPublicKey,
    override val alias: String?,
    override val keyScheme: KeyScheme,
    override val externalId: String?
) : SigningKeySaveContext