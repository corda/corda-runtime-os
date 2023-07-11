package net.corda.crypto.persistence

import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme

@Suppress("LongParameterList")
class SigningWrappedKeySaveContext(
    val key: GeneratedWrappedKey,
    val wrappingKeyAlias: String,
    override val externalId: String?,
    override val alias: String?,
    override val category: String,
    override val keyScheme: KeyScheme
) : SigningKeySaveContext