package net.corda.crypto.service.impl.signing

import net.corda.v5.cipher.suite.providers.generation.GeneratedPublicKey
import net.corda.v5.cipher.suite.scheme.KeyScheme

@Suppress("LongParameterList")
class SigningPublicKeySaveContext(
    val key: GeneratedPublicKey,
    override val alias: String?,
    override val keyScheme: KeyScheme,
    override val externalId: String?
) : SigningKeySaveContext