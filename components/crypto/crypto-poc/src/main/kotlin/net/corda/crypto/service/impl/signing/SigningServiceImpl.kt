package net.corda.crypto.service.impl.signing

import net.corda.crypto.service.SigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite
import net.corda.v5.cipher.suite.providers.generation.GeneratedKey
import net.corda.v5.cipher.suite.providers.generation.GeneratedPublicKey
import net.corda.v5.cipher.suite.providers.generation.GeneratedWrappedKey
import net.corda.v5.cipher.suite.providers.generation.KeyGenerationSpec
import net.corda.v5.cipher.suite.providers.signing.KeyMaterialSpec
import net.corda.v5.cipher.suite.providers.signing.SigningAliasSpec
import net.corda.v5.cipher.suite.providers.signing.SigningWrappedSpec
import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [SigningService::class])
class SigningServiceImpl @Activate constructor(
    @Reference(service = CryptoWorkerCipherSuite::class)
    private val suite: CryptoWorkerCipherSuite,
    @Reference(service = SigningKeyStore::class)
    private val store: SigningKeyStore
) : SigningService {
    companion object {
        private val logger = contextLogger()
    }

    data class OwnedKeyRecord(val publicKey: PublicKey, val data: SigningCachedKey)

    override fun generateKeyPair(
        tenantId: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey {
        logger.info("generateKeyPair(tenant={}, alias={}))", tenantId, alias)
        val handler = suite.findGenerateKeyHandler(scheme.codeName)
            ?: throw IllegalArgumentException("There is no key generation handler for the scheme $scheme.")
        if (alias != null && store.find(tenantId, alias) != null) {
            throw IllegalStateException("The key with alias $alias already exist for tenant $tenantId")
        }
        return try {
            val generatedKey = handler.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = scheme,
                    tenantId = tenantId,
                    alias = alias
                ),
                context
            )
            store.save(tenantId, toSaveKeyContext(generatedKey, alias, scheme, externalId))
            generatedKey.publicKey
        }  catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw RuntimeException(e.message, e)
        }
    }

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        metadata: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        val record = getOwnedKeyRecord(tenantId, publicKey)
        logger.debug { "sign(tenant=$tenantId, publicKey=${record.data.id})" }
        val scheme = suite.findKeyScheme(record.data.schemeCodeName)
            ?: throw IllegalArgumentException("The scheme ${record.data.schemeCodeName} is not supported.")
        val handler = suite.findSignDataHandler(scheme.codeName)
            ?: throw IllegalArgumentException("There is no signing handler for the scheme $scheme.")
        val spec = if (record.data.keyMaterial != null) {
            SigningWrappedSpec(
                tenantId = tenantId,
                publicKey = record.publicKey,
                keyMaterialSpec = KeyMaterialSpec(
                    keyMaterial = record.data.keyMaterial,
                    masterKeyAlias = record.data.masterKeyAlias,
                    encodingVersion = record.data.encodingVersion!!
                ),
                keyScheme = scheme,
                signatureSpec = signatureSpec
            )
        } else {
            SigningAliasSpec(
                tenantId = tenantId,
                publicKey = record.publicKey,
                hsmAlias = record.data.hsmAlias!!,
                keyScheme = scheme,
                signatureSpec = signatureSpec
            )
        }
        return try {
            val signedBytes = handler.sign(spec, data, metadata, context)
            DigitalSignature.WithKey(
                by = record.publicKey,
                bytes = signedBytes,
                context = context
            )
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw RuntimeException(e.message, e)
        }
    }

    private fun toSaveKeyContext(
        key: GeneratedKey,
        alias: String?,
        scheme: KeyScheme,
        externalId: String?
    ): SigningKeySaveContext =
        when (key) {
            is GeneratedPublicKey -> SigningPublicKeySaveContext(
                key = key,
                alias = alias,
                keyScheme = scheme,
                externalId = externalId
            )
            is GeneratedWrappedKey -> SigningWrappedKeySaveContext(
                key = key,
                externalId = externalId,
                alias = alias,
                keyScheme = scheme,
                masterKeyAlias = key.masterKeyAlias
            )
            else -> throw IllegalStateException("Unknown key generation response: ${key::class.java.name}")
        }

    @Suppress("NestedBlockDepth")
    private fun getOwnedKeyRecord(tenantId: String, publicKey: PublicKey): OwnedKeyRecord {
        if (publicKey is CompositeKey) {
            val leafKeysIdsChunks = publicKey.leafKeys.map {
                it.publicKeyId() to it
            }.chunked(KEY_LOOKUP_INPUT_ITEMS_LIMIT)
            for (chunk in leafKeysIdsChunks) {
                val found = store.lookup(tenantId, chunk.map { it.first })
                if (found.isNotEmpty()) {
                    for (key in chunk) {
                        val first = found.firstOrNull { it.id == key.first }
                        if (first != null) {
                            return OwnedKeyRecord(key.second, first)
                        }
                    }
                }
            }
            throw IllegalArgumentException(
                "The tenant $tenantId doesn't own any public key in '${publicKey.publicKeyId()}' composite key."
            )
        } else {
            return store.find(tenantId, publicKey)?.let { OwnedKeyRecord(publicKey, it) }
                ?: throw IllegalArgumentException(
                    "The tenant $tenantId doesn't own public key '${publicKey.publicKeyId()}'."
                )
        }
    }
}