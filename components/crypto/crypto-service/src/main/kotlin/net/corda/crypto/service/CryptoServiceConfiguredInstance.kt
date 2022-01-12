package net.corda.crypto.service

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
    val tenantId: String,
    val category: String,
    val defaultSignatureScheme: SignatureScheme,
    val wrappingKeyAlias: String,
    val instance: CryptoService
) {
    fun getSupportedSchemes(): List<String> =
        if(category.equals(CryptoConsts.CryptoCategories.FRESH_KEYS, ignoreCase = true)) {
            instance.supportedWrappingSchemes()
        } else {
            instance.supportedSchemes()
        }.map { it.codeName }

    fun containsKey(alias: String): Boolean
        = instance.containsKey(alias)

    fun createWrappingKey(failIfExists: Boolean)
        = instance.createWrappingKey(wrappingKeyAlias, failIfExists)

    fun findPublicKey(alias: String): PublicKey?
        = instance.findPublicKey(alias)

    fun generateKeyPair(alias: String, context: Map<String, String>): PublicKey
        = instance.generateKeyPair(alias, defaultSignatureScheme, context)

    fun generateWrappedKeyPair(context: Map<String, String>): WrappedKeyPair
        = instance.generateWrappedKeyPair(wrappingKeyAlias, defaultSignatureScheme, context)

    fun sign(alias: String, signatureScheme: SignatureScheme, data: ByteArray, context: Map<String, String>): ByteArray
        = instance.sign(alias, signatureScheme, data, context)

    fun sign(wrappedKey: WrappedPrivateKey, data: ByteArray, context: Map<String, String>): ByteArray
        = instance.sign(wrappedKey, data, context)

    fun computeHSMAlias(alias: String): String
        = Base32.toBase32String((tenantId + alias).encodeToByteArray().sha256Bytes()).take(30).toLowerCase()
}