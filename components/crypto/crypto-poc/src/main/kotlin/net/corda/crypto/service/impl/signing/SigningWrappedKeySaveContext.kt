package net.corda.crypto.service.impl.signing

import net.corda.v5.cipher.suite.handlers.generation.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyScheme

@Suppress("LongParameterList")
class SigningWrappedKeySaveContext(
    val key: GeneratedWrappedKey,
    val masterKeyAlias: String?,
    override val externalId: String?,
    override val alias: String?,
    override val keyScheme: KeyScheme,
) : SigningKeySaveContext