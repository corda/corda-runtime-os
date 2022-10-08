package net.corda.crypto.service.impl

import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite
import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.cipher.suite.handlers.generation.GeneratedKey
import net.corda.v5.cipher.suite.handlers.generation.GeneratedPublicKey
import net.corda.v5.cipher.suite.handlers.generation.GeneratedWrappedKey
import net.corda.v5.cipher.suite.handlers.generation.KeyGenerationSpec
import net.corda.v5.cipher.suite.handlers.signing.KeyMaterialSpec
import net.corda.v5.cipher.suite.handlers.signing.SigningAliasSpec
import net.corda.v5.cipher.suite.handlers.signing.SigningWrappedSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.security.PublicKey

@Component(service = [SigningService::class])
class SigningServiceImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoWorkerCipherSuite::class)
    private val suite: CryptoWorkerCipherSuite,
    @Reference(service = SigningKeyStore::class)
    private val store: SigningKeyStore
) : AbstractComponent<SigningServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<SigningService>(),
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<SigningKeyStore>()
        )
    )
), SigningService {
    override fun createActiveImpl(): Impl = Impl(suite, store, logger)

    class Impl(
        private val suite: CryptoWorkerCipherSuite,
        private val store: SigningKeyStore,
        private val logger: Logger
    ) : AbstractImpl, SigningService {
        data class OwnedKeyRecord(val publicKey: PublicKey, val data: SigningCachedKey)

        override fun getSupportedSchemes(): List<String> {
            logger.debug { "getSupportedSchemes()" }
            return suite.getAllSupportedKeySchemes().map { it.scheme.codeName }
        }

        override fun lookup(
            tenantId: String,
            skip: Int,
            take: Int,
            orderBy: KeyOrderBy,
            filter: Map<String, String>
        ): Collection<SigningKeyInfo> {
            logger.debug {
                "lookup(tenantId=$tenantId, skip=$skip, take=$take, orderBy=$orderBy, filter=[${filter.keys.joinToString()}]"
            }
            return store.lookup(
                tenantId,
                skip,
                take,
                orderBy.toSigningKeyOrderBy(),
                filter
            ).map { key -> key.toSigningKeyInfo() }
        }

        override fun lookup(tenantId: String, ids: List<String>): Collection<SigningKeyInfo> {
            logger.debug { "lookup(tenantId=$tenantId, ids=[${ids.joinToString()}])" }
            return store.lookup(tenantId, ids).map { key -> key.toSigningKeyInfo() }
        }

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
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Throwable) {
                throw RuntimeException(e.message, e)
            }
        }

        override fun freshKey(
            tenantId: String,
            category: String,
            scheme: KeyScheme,
            context: Map<String, String>
        ): PublicKey =
            generateKeyPair(
                tenantId = tenantId,
                alias = null,
                externalId = null,
                scheme = scheme,
                context = context
            )

        override fun freshKey(
            tenantId: String,
            category: String,
            externalId: String,
            scheme: KeyScheme,
            context: Map<String, String>
        ): PublicKey =
            generateKeyPair(
                tenantId = tenantId,
                alias = null,
                externalId = externalId,
                scheme = scheme,
                context = context
            )

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
                        keyMaterial = record.data.keyMaterial!!,
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

        private fun KeyOrderBy.toSigningKeyOrderBy(): SigningKeyOrderBy =
            SigningKeyOrderBy.valueOf(name)

        private fun SigningCachedKey.toSigningKeyInfo(): SigningKeyInfo =
            SigningKeyInfo(
                id = id,
                tenantId = tenantId,
                category = category,
                alias = alias,
                hsmAlias = hsmAlias,
                publicKey = publicKey,
                schemeCodeName = schemeCodeName,
                masterKeyAlias = masterKeyAlias,
                externalId = externalId,
                encodingVersion = encodingVersion,
                created = timestamp
            )

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
    }

    override fun getSupportedSchemes(): List<String> {
        TODO("Not yet implemented")
    }

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: KeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningKeyInfo> {
        TODO("Not yet implemented")
    }

    override fun lookup(tenantId: String, ids: List<String>): Collection<SigningKeyInfo> {
        TODO("Not yet implemented")
    }

    override fun generateKeyPair(
        tenantId: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey {
        TODO("Not yet implemented")
    }

    override fun freshKey(
        tenantId: String,
        category: String,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey {
        TODO("Not yet implemented")
    }

    override fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey {
        TODO("Not yet implemented")
    }

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        metadata: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        TODO("Not yet implemented")
    }
}
