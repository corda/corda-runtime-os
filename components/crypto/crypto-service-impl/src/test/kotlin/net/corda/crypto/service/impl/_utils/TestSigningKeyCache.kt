package net.corda.crypto.service.impl._utils

import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SigningKeyCacheProvider
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class TestSigningKeyCacheProvider(
    coordinatorFactory: LifecycleCoordinatorFactory
) : SigningKeyCacheProvider {
    private val instance = TestSigningKeyCache()

    val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<SigningKeyCacheProvider>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun getInstance(): SigningKeyCache {
        check(isRunning) {
            "The provider is in invalid state."
        }
        return instance
    }
}

class TestSigningKeyCache : SigningKeyCache {
    private val actions = ConcurrentHashMap<String, SigningKeyCacheActions>()
    override fun act(tenantId: String): SigningKeyCacheActions =
        actions.computeIfAbsent(tenantId) { TestSigningKeyCacheActions(tenantId) }
}

class TestSigningKeyCacheActions(
    private val tenantId: String
) : SigningKeyCacheActions {
    private val keys = ConcurrentHashMap<String, SigningCachedKey>()

    override fun save(context: SigningKeySaveContext) {
        val now = Instant.now()
        val record = when (context) {
            is SigningPublicKeySaveContext -> {
                val encodedKey = context.key.publicKey.encoded
                SigningCachedKey(
                    id = publicKeyIdOf(encodedKey),
                    tenantId = tenantId,
                    category = context.category,
                    alias = context.alias,
                    hsmAlias = context.key.hsmAlias,
                    publicKey = encodedKey,
                    keyMaterial = null,
                    schemeCodeName = context.signatureScheme.codeName,
                    masterKeyAlias = null,
                    externalId = null,
                    encodingVersion = null,
                    created = now
                )
            }
            is SigningWrappedKeySaveContext -> {
                val encodedKey = context.key.publicKey.encoded
                SigningCachedKey(
                    id = publicKeyIdOf(encodedKey),
                    tenantId = tenantId,
                    category = context.category,
                    alias = context.alias,
                    hsmAlias = null,
                    publicKey = encodedKey,
                    keyMaterial = context.key.keyMaterial,
                    schemeCodeName = context.signatureScheme.codeName,
                    masterKeyAlias = context.masterKeyAlias,
                    externalId = context.externalId,
                    encodingVersion = context.key.encodingVersion,
                    created = now
                )
            }
            else -> throw  IllegalArgumentException("Unknown type ${context::class.java.name}")
        }
        if(keys.putIfAbsent(record.id, record) != null) {
            throw IllegalArgumentException("The key ${record.id} already exists.")
        }
    }

    override fun find(alias: String): SigningCachedKey? =
        keys.values.firstOrNull { it.alias == alias }

    override fun find(publicKey: PublicKey): SigningCachedKey? =
        keys[publicKeyIdOf(publicKey)]

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        return candidateKeys.filter {
            keys.containsKey(publicKeyIdOf(it))
        }
    }

    override fun lookup(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        category: String?,
        schemeCodeName: String?,
        alias: String?,
        masterKeyAlias: String?,
        createdAfter: Instant?,
        createdBefore: Instant?
    ): List<SigningCachedKey> {
        val filtered = keys.values.filter {
            if(category != null && it.category != category) {
                false
            } else if(schemeCodeName != null && it.schemeCodeName != schemeCodeName) {
                false
            } else if(alias != null && it.alias != alias) {
                false
            } else if(masterKeyAlias != null && it.masterKeyAlias != masterKeyAlias) {
                false
            } else if(createdAfter != null && it.created < createdAfter) {
                false
            } else !(createdBefore != null && it.created > createdBefore)
        }
        return when(orderBy) {
            SigningKeyOrderBy.CREATED -> filtered.sortedBy { it.created }
            SigningKeyOrderBy.CATEGORY -> filtered.sortedBy { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME -> filtered.sortedBy { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS -> filtered.sortedBy { it.alias }
            SigningKeyOrderBy.MASTER_KEY_ALIAS -> filtered.sortedBy { it.masterKeyAlias }
            SigningKeyOrderBy.CREATED_DESC -> filtered.sortedByDescending { it.created }
            SigningKeyOrderBy.CATEGORY_DESC -> filtered.sortedByDescending { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME_DESC -> filtered.sortedByDescending { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS_DESC -> filtered.sortedByDescending { it.alias }
            SigningKeyOrderBy.MASTER_KEY_ALIAS_DESC -> filtered.sortedByDescending { it.masterKeyAlias }
            SigningKeyOrderBy.NONE -> filtered
        }.drop(skip).take(take)
    }

    override fun lookup(ids: List<String>): List<SigningCachedKey> {
        val result = mutableListOf<SigningCachedKey>()
        ids.forEach {
            val found = keys[it]
            if(found != null) {
                result.add(found)
            }
        }
        return result
    }

    override fun close() = Unit
}
