package net.corda.crypto.persistence.signing

import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.schemes.KeyScheme

@Suppress("LongParameterList")
class SigningWrappedKeySaveContext(
    val key: GeneratedWrappedKey,
    val masterKeyAlias: String?,
    override val externalId: String?,
    override val alias: String?,
    override val category: String,
    override val keyScheme: KeyScheme,
    override val workerSetId: String
) : SigningKeySaveContext