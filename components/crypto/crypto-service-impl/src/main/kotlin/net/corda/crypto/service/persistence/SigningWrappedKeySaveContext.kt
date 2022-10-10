package net.corda.crypto.service.persistence

import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.cipher.suite.handlers.generation.GeneratedWrappedKey

@Suppress("LongParameterList")
class SigningWrappedKeySaveContext(
    val key: GeneratedWrappedKey,
    val masterKeyAlias: String?,
    override val externalId: String?,
    override val alias: String?,
    override val keyScheme: KeyScheme,
) : SigningKeySaveContext