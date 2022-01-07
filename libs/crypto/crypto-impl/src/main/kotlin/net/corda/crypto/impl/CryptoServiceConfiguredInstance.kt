package net.corda.crypto.impl

import net.corda.crypto.CryptoConsts
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.util.encoders.Base32
import java.security.PublicKey

/**
 * Defines an [CryptoService] instance and configured information
 */
class CryptoServiceConfiguredInstance(
    private val category: String,
    val defaultSignatureScheme: SignatureScheme,
    private val wrappingKeyAlias: String,
    val instance: CryptoService
) {
    fun getSupportedSchemes(): List<String> =
        if(category == CryptoConsts.CryptoCategories.FRESH_KEYS) {
            instance.supportedWrappingSchemes()
        } else {
            instance.supportedSchemes()
        }.map { it.codeName }

    fun containsKey(alias: String): Boolean =
        instance.containsKey(alias)

    fun createWrappingKey(failIfExists: Boolean) =
        instance.createWrappingKey(wrappingKeyAlias, failIfExists)

    fun findPublicKey(alias: String): PublicKey? =
        instance.findPublicKey(alias)

    fun generateKeyPair(alias: String, context: Map<String, String>): PublicKey =
        instance.generateKeyPair(alias, defaultSignatureScheme, context)

    fun generateWrappedKeyPair(context: Map<String, String>): WrappedKeyPair =
        instance.generateWrappedKeyPair(wrappingKeyAlias, defaultSignatureScheme, context)

    fun requiresWrappingKey(): Boolean =
        instance.requiresWrappingKey()

    fun sign(alias: String, data: ByteArray, context: Map<String, String>): ByteArray =
        instance.sign(alias, defaultSignatureScheme, data, context)

    fun sign(wrappedKey: WrappedPrivateKey, data: ByteArray, context: Map<String, String>): ByteArray =
        instance.sign(wrappedKey, data, context)

    fun computeHSMAlias(alias: String): String =
        Base32.toBase32String(alias.encodeToByteArray().sha256Bytes()).take(30).toUpperCase()
}